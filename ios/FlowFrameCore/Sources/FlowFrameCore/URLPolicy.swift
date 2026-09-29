import Foundation

public struct SupportedLink: Sendable {
    public let url: URL
    public let platform: MediaPlatform

    public init(url: URL, platform: MediaPlatform) {
        self.url = url
        self.platform = platform
    }
}

public enum ShareLinkParser {
    public static func parse(_ text: String) throws -> SupportedLink {
        guard text.utf8.count <= 16 * 1024 else { throw FlowFrameError.message("分享内容超过 16 KiB，请只粘贴一个作品链接。") }
        // ASCII boundaries stop at Chinese share-copy punctuation without changing URL bytes.
        let expression = try NSRegularExpression(pattern: #"https?://[^\s<>"'()\[\]{},;!`|\u0080-\uFFFF]+"#, options: .caseInsensitive)
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        var links: [SupportedLink] = []
        var observed = Set<String>()
        for match in expression.matches(in: text, range: range) {
            guard let range = Range(match.range, in: text) else { continue }
            let raw = String(text[range]).trimmingCharacters(in: CharacterSet(charactersIn: ".,:;!?)]}"))
            guard let link = validate(raw) else { throw FlowFrameError.message("只支持抖音与哔哩哔哩的公开作品链接。") }
            if observed.insert(link.url.absoluteString).inserted { links.append(link) }
        }
        guard links.count == 1, let link = links.first else {
            throw FlowFrameError.message(links.isEmpty ? "未找到支持的作品链接。" : "检测到多个作品链接，请每次只粘贴一个。")
        }
        return link
    }

    static func validate(_ raw: String) -> SupportedLink? {
        guard raw.utf8.count <= 2048, !raw.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }),
              let components = URLComponents(string: raw), let scheme = components.scheme?.lowercased(),
              ["https", "http"].contains(scheme), components.user == nil, components.password == nil,
              let originalHost = components.host?.lowercased(), !originalHost.isEmpty,
              let authority = raw.firstMatch(#"^https?://([^/?#]+)"#, options: .caseInsensitive, group: 1) else { return nil }
        let host = originalHost.hasSuffix(".") ? String(originalHost.dropLast()) : originalHost
        let defaultPort = scheme == "https" ? 443 : 80
        guard components.port == nil || components.port == defaultPort,
              [host, host + ".", "\(host):\(defaultPort)", "\(host).:\(defaultPort)"].contains(authority.lowercased()) else { return nil }
        let path = components.percentEncodedPath
        let platform: MediaPlatform
        var short = false
        if host == "v.douyin.com", path.matches(#"^/[A-Za-z0-9_-]{1,128}/?$"#) {
            platform = .douyin; short = true
        } else if PageURLPolicy.douyinWeb.contains(host), path.matches(#"^/(video|note)/[0-9]{1,32}/?$"#) {
            platform = .douyin
        } else if ["iesdouyin.com", "www.iesdouyin.com"].contains(host), path.matches(#"^/share/(video|note)/[0-9]{1,32}/?$"#) {
            platform = .douyin
        } else if PageURLPolicy.bilibiliShort.contains(host), path.matches(#"^/[A-Za-z0-9_-]{1,128}/?$"#) {
            platform = .bilibili; short = true
        } else if PageURLPolicy.bilibiliWeb.contains(host), path.matches(#"^/video/BV[A-Za-z0-9]{8,20}/?$"#) {
            platform = .bilibili
        } else { return nil }
        guard scheme == "https" || short else { return nil }
        var normalized = components
        normalized.scheme = "https"
        normalized.host = host
        normalized.port = nil
        normalized.fragment = nil
        guard let url = normalized.url else { return nil }
        return SupportedLink(url: url, platform: platform)
    }
}

public enum MediaURLPolicy {
    /// Exact registrable CDN domains; a suffix must start on a DNS label boundary.
    /// Numeric/private/localhost hosts cannot match this allowlist.
    private static let domains = [
        "douyinvod.com", "douyinpic.com", "douyincdn.com", "byteimg.com", "bytecdn.cn", "pstatp.com",
        "bilivideo.com", "bilivideo.cn", "hdslb.com", "biliimg.com"
    ]
    private static let exactHosts = ["aweme.snssdk.com", "aweme-hl.snssdk.com", "www.iesdouyin.com"]

    public static func validate(_ url: URL) -> Bool {
        guard let host = secureHost(url), host.unicodeScalars.allSatisfy({ $0.isASCII }),
              !host.hasSuffix("."), !host.contains("%") else { return false }
        return exactHosts.contains(host) || domains.contains { host == $0 || host.hasSuffix("." + $0) }
    }

    static func normalized(_ string: String?) -> URL? {
        guard var string = string, !string.isEmpty else { return nil }
        if string.hasPrefix("//") { string = "https:" + string }
        // Public metadata sometimes advertises HTTP CDN links: upgrade before validation/request.
        if string.hasPrefix("http://") { string = "https://" + string.dropFirst(7) }
        guard let url = URL(string: string), validate(url) else { return nil }
        return url
    }

    static func secureHost(_ url: URL) -> String? {
        guard url.absoluteString.utf8.count <= 8192,
              let c = URLComponents(url: url, resolvingAgainstBaseURL: false), c.scheme?.lowercased() == "https",
              c.user == nil, c.password == nil, c.port == nil || c.port == 443,
              let host = c.host?.lowercased(), !host.isEmpty,
              !url.absoluteString.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) else { return nil }
        return host
    }
}

enum PageURLPolicy {
    static let douyinWeb = ["douyin.com", "www.douyin.com", "m.douyin.com"]
    static let bilibiliWeb = ["bilibili.com", "www.bilibili.com", "m.bilibili.com"]
    static let bilibiliShort = ["b23.tv", "bili2233.cn", "www.bili2233.cn"]

    static func validate(_ url: URL, platform: MediaPlatform) -> Bool {
        guard let host = MediaURLPolicy.secureHost(url) else { return false }
        switch platform {
        case .douyin: return (douyinWeb + ["v.douyin.com", "iesdouyin.com", "www.iesdouyin.com"]).contains(host)
        case .bilibili: return (bilibiliWeb + bilibiliShort + ["api.bilibili.com"]).contains(host)
        }
    }
}

extension String {
    func matches(_ pattern: String) -> Bool { range(of: pattern, options: .regularExpression) != nil }
    func firstMatch(_ pattern: String, options: NSRegularExpression.Options = [], group: Int = 0) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: options),
              let result = regex.firstMatch(in: self, range: NSRange(startIndex..<endIndex, in: self)),
              group < result.numberOfRanges, let range = Range(result.range(at: group), in: self) else { return nil }
        return String(self[range])
    }
}
