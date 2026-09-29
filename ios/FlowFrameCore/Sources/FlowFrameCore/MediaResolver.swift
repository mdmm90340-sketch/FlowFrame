import Foundation

public actor MediaResolver {
    public init() {}

    public func resolve(_ text: String) async throws -> MediaPreview {
        let link = try ShareLinkParser.parse(text)
        try Task.checkCancellation()
        switch link.platform {
        case .douyin: return try await resolveDouyin(link)
        case .bilibili: return try await resolveBilibili(link)
        }
    }

    private func expanded(_ link: SupportedLink) async throws -> (URL, Data?) {
        let path = link.url.path
        if path.matches(#"^/(?:share/)?(?:video|note)/[0-9]+/?$"#) || path.matches(#"^/video/BV[A-Za-z0-9]+/?$"#) {
            return (link.url, nil)
        }
        let page = try await PublicHTTP.get(link.url, platform: link.platform)
        guard let expanded = ShareLinkParser.validate(page.url.absoluteString), expanded.platform == link.platform else {
            throw FlowFrameError.message("短链接没有跳转到支持的作品页面。")
        }
        return (expanded.url, page.data)
    }

    private func resolveDouyin(_ link: SupportedLink) async throws -> MediaPreview {
        let (url, existing) = try await expanded(link)
        guard let id = url.path.firstMatch(#"/(?:video|note)/([0-9]{1,32})/?$"#, group: 1) else { throw PublicMediaParser.unavailable }
        if let existing = existing, let html = String(data: existing, encoding: .utf8),
           let result = try? PublicMediaParser.douyinHTML(html, id: id, sourceURL: url) { return result }
        // Share pages can expose public item JSON without the web app's signed API or cookies.
        // Platform challenges remain errors; no login session or downloaded script is executed.
        let share = URL(string: "https://www.iesdouyin.com/share/video/\(id)/")!
        var candidates = [share, url]
        candidates.append(URL(string: "https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=\(id)")!)
        var visited = Set<URL>()
        var lastError: Error = PublicMediaParser.unavailable
        for candidate in candidates where visited.insert(candidate).inserted {
            do {
                let page = try await PublicHTTP.get(candidate, platform: .douyin, mobile: candidate == share)
                try Task.checkCancellation()
                if let preview = try? PublicMediaParser.douyinJSON(page.data, id: id, sourceURL: url) { return preview }
                guard let html = String(data: page.data, encoding: .utf8) else { throw PublicMediaParser.unavailable }
                return try PublicMediaParser.douyinHTML(html, id: id, sourceURL: url)
            } catch {
                try Task.checkCancellation()
                if let urlError = error as? URLError, urlError.code == .cancelled { throw CancellationError() }
                lastError = error
            }
        }
        if let domainError = lastError as? FlowFrameError { throw domainError }
        throw PublicMediaParser.unavailable
    }

    private func resolveBilibili(_ link: SupportedLink) async throws -> MediaPreview {
        let (url, _) = try await expanded(link)
        guard let bvid = url.path.firstMatch(#"/video/(BV[A-Za-z0-9]{8,20})/?$"#, group: 1) else { throw PublicMediaParser.unavailable }
        let pageItems = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.filter { $0.name == "p" } ?? []
        guard pageItems.count <= 1 else { throw FlowFrameError.message("链接包含多个分 P 参数。") }
        let pageText = pageItems.first?.value ?? "1"
        guard pageText.matches(#"^[0-9]{1,4}$"#), let page = Int(pageText), page > 0 else {
            throw FlowFrameError.message("分 P 参数无效。")
        }
        let viewURL = URL(string: "https://api.bilibili.com/x/web-interface/view?bvid=\(bvid)")!
        let view = try await PublicHTTP.get(viewURL, platform: .bilibili)
        let metadata = try PublicMediaParser.bilibiliMetadata(view.data, bvid: bvid, page: page)
        let playURL = URL(string: "https://api.bilibili.com/x/player/playurl?bvid=\(bvid)&cid=\(metadata.cid)&qn=80&fnval=16&fnver=0&fourk=0")!
        let playback = try await PublicHTTP.get(playURL, platform: .bilibili)
        try Task.checkCancellation()
        return try PublicMediaParser.bilibiliPlayback(playback.data, metadata: metadata, sourceURL: url)
    }
}

enum PublicHTTP {
    static let userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    static let mobileUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    static let maxBytes = 8 * 1024 * 1024

    struct Page { let url: URL; let data: Data }

    static func get(_ url: URL, platform: MediaPlatform, mobile: Bool = false) async throws -> Page {
        var current = url
        for hop in 0...5 {
            try Task.checkCancellation()
            guard PageURLPolicy.validate(current, platform: platform) else { throw FlowFrameError.message("页面跳转离开原平台或使用了不安全地址。") }
            let response = try await oneRequest(current, platform: platform, mobile: mobile)
            if let location = response.redirect {
                guard hop < 5, let target = URL(string: location, relativeTo: current)?.absoluteURL,
                      PageURLPolicy.validate(target, platform: platform) else {
                    throw FlowFrameError.message("页面重定向过多，或跳转离开了原平台。")
                }
                current = target
            } else {
                return Page(url: current, data: response.data)
            }
        }
        throw PublicMediaParser.unavailable
    }

    private struct Response { let data: Data; let redirect: String? }

    private static func oneRequest(_ url: URL, platform: MediaPlatform, mobile: Bool) async throws -> Response {
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieStorage = nil
        config.httpShouldSetCookies = false
        config.urlCredentialStorage = nil
        config.urlCache = nil
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 45
        let session = URLSession(configuration: config)
        defer { session.invalidateAndCancel() }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 20)
        request.httpShouldHandleCookies = false
        request.setValue(mobile ? mobileUserAgent : userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/json;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
        request.setValue(platform == .douyin ? "https://www.douyin.com/" : "https://www.bilibili.com/", forHTTPHeaderField: "Referer")
        let (bytes, rawResponse) = try await session.bytes(for: request, delegate: NoAutomaticRedirects())
        guard let response = rawResponse as? HTTPURLResponse else { throw PublicMediaParser.unavailable }
        if [301, 302, 303, 307, 308].contains(response.statusCode) {
            guard let location = response.value(forHTTPHeaderField: "Location") else { throw PublicMediaParser.unavailable }
            return Response(data: Data(), redirect: location)
        }
        guard (200...299).contains(response.statusCode) else {
            throw FlowFrameError.message("平台请求失败（HTTP \(response.statusCode)）；可能暂时限制匿名访问。")
        }
        guard response.expectedContentLength <= Int64(maxBytes) else { throw FlowFrameError.message("平台页面超过 8 MiB 大小限制。") }
        var data = Data()
        for try await byte in bytes {
            if data.count % 16_384 == 0 { try Task.checkCancellation() }
            guard data.count < maxBytes else { throw FlowFrameError.message("平台页面超过 8 MiB 大小限制。") }
            data.append(byte)
        }
        try Task.checkCancellation()
        return Response(data: data, redirect: nil)
    }
}

private final class NoAutomaticRedirects: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}
