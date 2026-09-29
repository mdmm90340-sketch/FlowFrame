import Foundation
import FlowFrameCore

/// One session per transfer keeps cancellation and staging files isolated.
final class MediaTransfer: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<URL, Error>?
    private var session: URLSession?
    private var downloadTask: URLSessionDownloadTask?
    private var wasCancelled = false
    private var redirectCount = 0
    private let destination: URL
    private let kind: MediaKind
    private let sizeLimit: Int64
    private let progress: @Sendable (Double?) -> Void

    init(destination: URL, kind: MediaKind, sizeLimit: Int64 = 2_147_483_648,
         progress: @escaping @Sendable (Double?) -> Void = { _ in }) {
        self.destination = destination
        self.kind = kind
        self.sizeLimit = sizeLimit
        self.progress = progress
    }

    func fetch(_ url: URL, headers: [String: String]) async throws -> URL {
        guard MediaURLPolicy.validate(url) else {
            throw DownloadFailure.message("下载地址未通过安全检查，请重新解析。")
        }
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                lock.lock()
                guard !wasCancelled else {
                    lock.unlock()
                    continuation.resume(throwing: CancellationError())
                    return
                }
                self.continuation = continuation
                let configuration = URLSessionConfiguration.ephemeral
                configuration.timeoutIntervalForRequest = 45
                configuration.timeoutIntervalForResource = 30 * 60
                configuration.httpCookieStorage = nil
                configuration.httpShouldSetCookies = false
                configuration.urlCredentialStorage = nil
                configuration.urlCache = nil
                let queue = OperationQueue()
                queue.maxConcurrentOperationCount = 1
                let session = URLSession(configuration: configuration, delegate: self, delegateQueue: queue)
                var request = URLRequest(url: url)
                for (key, value) in headers where ["user-agent", "referer", "accept", "origin"].contains(key.lowercased()) {
                    request.setValue(value, forHTTPHeaderField: key)
                }
                let task = session.downloadTask(with: request)
                self.session = session
                downloadTask = task
                lock.unlock()
                task.resume()
            }
        } onCancel: {
            self.cancel()
        }
    }

    private func cancel() {
        lock.lock()
        wasCancelled = true
        let task = downloadTask
        lock.unlock()
        task?.cancel()
    }

    private func finish(_ result: Result<URL, Error>) {
        lock.lock()
        let continuation = self.continuation
        self.continuation = nil
        let session = self.session
        self.session = nil
        downloadTask = nil
        lock.unlock()
        continuation?.resume(with: result)
        session?.finishTasksAndInvalidate()
    }

    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        redirectCount += 1
        guard redirectCount <= 8, let url = request.url, MediaURLPolicy.validate(url) else {
            completionHandler(nil)
            task.cancel()
            finish(.failure(DownloadFailure.message("下载跳转未通过安全检查，请重新解析。")))
            return
        }
        var safeRequest = request
        safeRequest.setValue(nil, forHTTPHeaderField: "Authorization")
        safeRequest.setValue(nil, forHTTPHeaderField: "Cookie")
        completionHandler(safeRequest)
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                    didWriteData bytesWritten: Int64, totalBytesWritten: Int64,
                    totalBytesExpectedToWrite: Int64) {
        guard totalBytesWritten <= sizeLimit,
              totalBytesExpectedToWrite <= sizeLimit else {
            downloadTask.cancel()
            finish(.failure(DownloadFailure.message("文件超过当前版本的大小限制（2 GB）。")))
            return
        }
        progress(totalBytesExpectedToWrite > 0 ? min(Double(totalBytesWritten) / Double(totalBytesExpectedToWrite), 1) : nil)
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                    didFinishDownloadingTo location: URL) {
        do {
            try Task.checkCancellation()
            lock.lock()
            let cancelled = wasCancelled
            lock.unlock()
            guard !cancelled else { throw CancellationError() }
            guard let response = downloadTask.response as? HTTPURLResponse,
                  (200...299).contains(response.statusCode),
                  let url = response.url, MediaURLPolicy.validate(url) else {
                throw DownloadFailure.message("服务器未返回可下载的文件，链接可能已失效，请重试。")
            }
            let mime = (response.mimeType ?? "").lowercased()
            guard !mime.contains("text"), !mime.contains("json"), !mime.contains("html") else {
                throw DownloadFailure.message("服务器返回了网页或错误信息，请重新解析。")
            }
            let size = try location.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            guard size > 0, Int64(size) <= sizeLimit else {
                throw DownloadFailure.message("下载文件为空或超出大小限制。")
            }
            try Self.validateSignature(at: location, kind: kind)
            try FileManager.default.moveItem(at: location, to: destination)
            finish(.success(destination))
        } catch {
            finish(.failure(error))
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if let error { finish(.failure(error)) }
    }

    /// Reject HTML/JSON masquerading as media, even with a successful HTTP status.
    private static func validateSignature(at url: URL, kind: MediaKind) throws {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        let bytes = [UInt8](try handle.read(upToCount: 64) ?? Data())
        let box = bytes.count >= 8 ? String(bytes: bytes[4..<8], encoding: .ascii) : nil
        let isISO = ["ftyp", "styp", "moof"].contains(box ?? "")
        let valid: Bool
        switch kind {
        case .video: valid = isISO
        case .audio:
            valid = isISO || bytes.starts(with: [0x49, 0x44, 0x33]) ||
                (bytes.count > 1 && bytes[0] == 0xff && bytes[1] & 0xe0 == 0xe0)
        case .image:
            valid = bytes.starts(with: [0xff, 0xd8, 0xff]) ||
                bytes.starts(with: [0x89, 0x50, 0x4e, 0x47]) ||
                bytes.starts(with: [0x47, 0x49, 0x46, 0x38]) ||
                (bytes.count >= 12 && String(bytes: bytes[0..<4], encoding: .ascii) == "RIFF" &&
                 String(bytes: bytes[8..<12], encoding: .ascii) == "WEBP") || isISO
        }
        guard valid else {
            throw DownloadFailure.message("文件格式不受此版本支持，或服务器没有返回有效媒体。")
        }
    }
}
