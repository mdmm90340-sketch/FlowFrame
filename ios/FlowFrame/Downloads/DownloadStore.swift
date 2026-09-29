import Foundation
import FlowFrameCore
import UIKit

@MainActor
final class DownloadStore: ObservableObject {
    @Published private(set) var records: [DownloadRecord] = []
    @Published private(set) var storageWarning: String?
    private let resolver = MediaResolver()
    private let supportDirectory: URL
    private let downloadsDirectory: URL
    private let recordsURL: URL
    private var worker: Task<Void, Never>?
    private var activeID: UUID?
    private var foreground = true
    private var backgroundID: UIBackgroundTaskIdentifier = .invalid

    init() {
        let files = FileManager.default
        supportDirectory = files.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("FlowFrame", isDirectory: true)
        downloadsDirectory = files.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Downloads", isDirectory: true)
        recordsURL = supportDirectory.appendingPathComponent("tasks.json")
        do {
            try files.createDirectory(at: supportDirectory, withIntermediateDirectories: true)
            try files.createDirectory(at: downloadsDirectory, withIntermediateDirectories: true)
            let staging = supportDirectory.appendingPathComponent("Staging", isDirectory: true)
            if files.fileExists(atPath: staging.path) { try files.removeItem(at: staging) }
            try files.createDirectory(at: staging, withIntermediateDirectories: true)
            if files.fileExists(atPath: recordsURL.path) {
                records = try JSONDecoder().decode([DownloadRecord].self, from: Data(contentsOf: recordsURL))
                for index in records.indices {
                    if records[index].status.isPending {
                        records[index].status = .failed
                        records[index].progress = nil
                        records[index].detail = "上次下载已中断。重试会刷新链接并重新下载。"
                    } else if records[index].status == .completed,
                              let name = records[index].fileName,
                              !files.fileExists(atPath: downloadsDirectory.appendingPathComponent(name).path) {
                        records[index].status = .failed
                        records[index].detail = "已下载文件被移动或删除，可以重试下载。"
                        records[index].fileName = nil
                    }
                }
                persist()
            }
        } catch {
            storageWarning = "下载记录无法读取或保存。已保存的媒体仍可在“文件”App 中找到。"
        }
    }

    var pendingCount: Int { records.filter { $0.status.isPending }.count }

    func enqueue(preview: MediaPreview, assets: [MediaAsset], sourceText: String) {
        for asset in assets {
            records.insert(DownloadRecord(preview: preview, asset: asset, sourceText: sourceText), at: 0)
        }
        persist()
        startNext()
    }

    func retry(_ id: UUID) {
        guard let index = records.firstIndex(where: { $0.id == id }),
              [.failed, .cancelled].contains(records[index].status) else { return }
        records[index].status = .queued
        records[index].detail = nil
        records[index].progress = nil
        persist()
        startNext()
    }

    func cancel(_ id: UUID) {
        guard let index = records.firstIndex(where: { $0.id == id }), records[index].status.isPending else { return }
        records[index].status = .cancelled
        records[index].progress = nil
        records[index].detail = "临时文件已清理，可以重新下载。"
        if activeID == id { worker?.cancel() }
        persist()
    }

    func removeRecord(_ id: UUID) {
        cancel(id)
        records.removeAll { $0.id == id }
        persist()
    }

    func fileURL(for record: DownloadRecord) -> URL? {
        guard record.status == .completed, let name = record.fileName,
              name == (name as NSString).lastPathComponent else { return nil }
        let url = downloadsDirectory.appendingPathComponent(name)
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }

    func setForeground(_ value: Bool) {
        foreground = value
        if value {
            endBackgroundTime()
            startNext()
        } else if activeID != nil, backgroundID == .invalid {
            backgroundID = UIApplication.shared.beginBackgroundTask(withName: "Finish current FlowFrame download") { [weak self] in
                Task { @MainActor in self?.interruptForBackground() }
            }
            if backgroundID == .invalid { interruptForBackground() }
        }
    }

    private func interruptForBackground() {
        if let id = activeID {
            update(id, status: .failed, detail: "下载因应用进入后台而中断，请保持前台后重试。")
            worker?.cancel()
        }
        endBackgroundTime()
    }

    private func endBackgroundTime() {
        if backgroundID != .invalid {
            UIApplication.shared.endBackgroundTask(backgroundID)
            backgroundID = .invalid
        }
    }

    private func startNext() {
        guard foreground, worker == nil,
              let record = records.reversed().first(where: { $0.status == .queued }) else { return }
        activeID = record.id
        worker = Task { [weak self] in
            guard let self else { return }
            await self.perform(record)
            self.worker = nil
            self.activeID = nil
            self.endBackgroundTime()
            self.startNext()
        }
    }

    private func perform(_ record: DownloadRecord) async {
        let staging = supportDirectory.appendingPathComponent("Staging", isDirectory: true)
            .appendingPathComponent(record.id.uuidString, isDirectory: true)
        defer { try? FileManager.default.removeItem(at: staging) }
        do {
            try FileManager.default.createDirectory(at: staging, withIntermediateDirectories: true)
            update(record.id, status: .resolving)
            let preview = try await resolver.resolve(record.sourceText)
            try Task.checkCancellation()
            guard let asset = preview.assets.first(where: { $0.id == record.assetID && $0.kind == record.kind }) else {
                throw DownloadFailure.message("原先选择的资源已不可用，请返回解析页重新选择。")
            }
            update(record.id, status: .downloading, detail: asset.companionAudioURL == nil ? "正在下载文件" : "正在下载视频轨道（1/2）")
            let ext = Self.safeExtension(asset.fileExtension, kind: asset.kind)
            let primary = staging.appendingPathComponent("media.\(ext)")
            let transfer = MediaTransfer(destination: primary, kind: asset.kind) { [weak self] progress in
                Task { @MainActor in self?.setProgress(record.id, progress: progress) }
            }
            var result = try await transfer.fetch(asset.url, headers: asset.headers)
            try Task.checkCancellation()
            if let audioURL = asset.companionAudioURL {
                update(record.id, status: .downloading, detail: "正在下载音频轨道（2/2）")
                let audio = staging.appendingPathComponent("audio.m4a")
                let audioTransfer = MediaTransfer(destination: audio, kind: .audio) { [weak self] progress in
                    Task { @MainActor in self?.setProgress(record.id, progress: progress) }
                }
                _ = try await audioTransfer.fetch(audioURL, headers: asset.headers)
                try Task.checkCancellation()
                update(record.id, status: .merging, detail: "正在生成含声音的 MP4 文件")
                result = try await MediaExporter.merge(video: primary, audio: audio, destination: staging.appendingPathComponent("merged.mp4"))
            } else {
                try await MediaExporter.validate(result, kind: asset.kind)
            }
            try Task.checkCancellation()
            guard records.contains(where: { $0.id == record.id && $0.status.isPending }) else { throw CancellationError() }
            let title = String(record.title.filter { !"/\\:*?\"<>|".contains($0) && !$0.isNewline }.prefix(48))
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let finalExtension = asset.companionAudioURL == nil ? ext : "mp4"
            let name = "\(title.isEmpty ? "FlowFrame" : title)-\(record.id.uuidString.prefix(8)).\(finalExtension)"
            try FileManager.default.moveItem(at: result, to: downloadsDirectory.appendingPathComponent(name))
            if let index = records.firstIndex(where: { $0.id == record.id }) {
                records[index].fileName = name
            }
            update(record.id, status: .completed, detail: "已保存到“文件”App · FlowFrame / Downloads")
        } catch {
            guard let existing = records.first(where: { $0.id == record.id }), existing.status.isPending else { return }
            if Task.isCancelled || error is CancellationError {
                update(record.id, status: .cancelled, detail: "临时文件已清理，可以重新下载。")
            } else {
                let message: String
                if let failure = error as? DownloadFailure { message = failure.localizedDescription }
                else if let failure = error as? FlowFrameError { message = failure.localizedDescription }
                else if (error as NSError).domain == NSURLErrorDomain { message = "网络连接失败或下载超时，请检查网络后重试。" }
                else { message = "无法完成下载，请检查可用空间，或重新解析后再试。" }
                update(record.id, status: .failed, detail: message)
            }
        }
    }

    private static func safeExtension(_ candidate: String, kind: MediaKind) -> String {
        let value = candidate.lowercased()
        switch kind {
        case .video: return "mp4"
        case .audio: return ["m4a", "mp3", "aac"].contains(value) ? value : "m4a"
        case .image: return ["jpg", "jpeg", "png", "webp", "gif", "avif", "heic"].contains(value) ? value : "jpg"
        }
    }

    private func setProgress(_ id: UUID, progress: Double?) {
        guard let index = records.firstIndex(where: { $0.id == id }), records[index].status == .downloading else { return }
        if let progress, let previous = records[index].progress, abs(progress - previous) < 0.01 { return }
        records[index].progress = progress
    }

    private func update(_ id: UUID, status: DownloadStatus, detail: String? = nil) {
        guard let index = records.firstIndex(where: { $0.id == id }) else { return }
        records[index].status = status
        records[index].detail = detail
        records[index].progress = nil
        persist()
    }

    private func persist() {
        do {
            let data = try JSONEncoder().encode(records)
            try data.write(to: recordsURL, options: .atomic)
            storageWarning = nil
        } catch {
            storageWarning = "下载记录暂时无法保存，请检查设备可用空间。"
        }
    }
}
