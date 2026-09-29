import Foundation

/// Deterministic parsing is kept separate from requests so fixtures exercise actual selection rules.
enum PublicMediaParser {
    static let unavailable = FlowFrameError.message("公开页面未提供可下载媒体；作品可能需要登录、已删除，或平台暂时限制匿名访问。")
    static let mixed = FlowFrameError.message("当前 iOS 版不支持混合图文视频、多视频作品或分段 FLV。")

    static func douyinJSON(_ data: Data, id: String, sourceURL: URL) throws -> MediaPreview {
        let root = try JSONSerialization.jsonObject(with: data)
        var remaining = 20_000
        guard let detail = findDouyin(root, id: id, depth: 0, remaining: &remaining) else { throw unavailable }
        return try douyinDetail(detail, id: id, sourceURL: sourceURL)
    }

    static func douyinHTML(_ html: String, id: String, sourceURL: URL) throws -> MediaPreview {
        let markers = ["window._ROUTER_DATA", "window.__INITIAL_STATE__", "window.__NEXT_DATA__", "window.SIGI_STATE"]
        for marker in markers {
            if let data = embeddedObject(html, marker: marker),
               let preview = try? douyinJSON(data, id: id, sourceURL: sourceURL) { return preview }
        }
        // RENDER_DATA is percent-encoded JSON, not JavaScript. Never execute page scripts.
        for identifier in ["RENDER_DATA", "__NEXT_DATA__", "SIGI_STATE"] {
            let pattern = #"<script\b[^>]*\bid=["']"# + identifier + #"["'][^>]*>(.*?)</script>"#
            if let raw = html.firstMatch(pattern, options: [.caseInsensitive, .dotMatchesLineSeparators], group: 1) {
                let decoded = identifier == "RENDER_DATA" ? raw.removingPercentEncoding ?? raw : raw
                if let data = decoded.data(using: .utf8), let preview = try? douyinJSON(data, id: id, sourceURL: sourceURL) { return preview }
            }
        }
        let title = html.firstMatch(#"<title[^>]*>(.*?)</title>"#, options: [.caseInsensitive, .dotMatchesLineSeparators], group: 1) ?? ""
        if title.contains("验证") || title.lowercased().contains("captcha") { throw FlowFrameError.message("抖音要求安全验证，当前匿名解析无法继续。") }
        throw unavailable
    }

    private static func findDouyin(_ value: Any, id: String, depth: Int, remaining: inout Int) -> [String: Any]? {
        guard depth < 48, remaining > 0 else { return nil }
        remaining -= 1
        if let object = value as? [String: Any] {
            // Do not use generic `id`: author, music and recommended-post IDs are unrelated.
            let identity = object.string("aweme_id") ?? object.string("awemeId")
            if identity == id, object["video"] != nil || object["images"] != nil { return object }
            for key in object.keys.sorted() {
                if let child = object[key], let found = findDouyin(child, id: id, depth: depth + 1, remaining: &remaining) { return found }
            }
        } else if let array = value as? [Any] {
            for child in array {
                if let found = findDouyin(child, id: id, depth: depth + 1, remaining: &remaining) { return found }
            }
        }
        return nil
    }

    private static func douyinDetail(_ detail: [String: Any], id: String, sourceURL: URL) throws -> MediaPreview {
        let status = detail.object("status")
        guard status.integer("is_private") == 0, status.integer("is_delete") == 0,
              status.integer("is_prohibited") == 0, detail.integer("is_live") == 0 else { throw unavailable }
        if detail["live_room"] != nil, !(detail["live_room"] is NSNull) { throw unavailable }
        let title = detail.string("desc")?.nonempty ?? "未命名抖音作品"
        let uploader = detail.object("author").string("nickname")
        let video = detail.object("video")
        let headers = ["Referer": "https://www.douyin.com/", "User-Agent": PublicHTTP.userAgent]
        let images = detail.array("images")
        var assets: [MediaAsset] = []
        if !images.isEmpty {
            guard images.count <= 100, detail.array("videos").isEmpty,
                  firstMediaURL(video.object("play_addr")["url_list"]) == nil else { throw mixed }
            for (index, element) in images.enumerated() {
                guard let image = element as? [String: Any], let url = firstMediaURL(image["url_list"]) else { throw unavailable }
                if let embeddedVideo = image["video"], !(embeddedVideo is NSNull) { throw mixed }
                assets.append(MediaAsset(id: "image-\(index)", url: url, kind: .image,
                                         label: "图片 \(index + 1)", fileExtension: imageExtension(url), headers: headers))
            }
            let music = detail.object("music")
            if let audio = firstMediaURL(music.object("play_url")["url_list"]) {
                assets.append(MediaAsset(id: "gallery-audio", url: audio, kind: .audio,
                                         label: "图集原始音频", fileExtension: "mp3", headers: headers))
            }
        } else {
            guard detail.array("videos").count <= 1 else { throw mixed }
            var seen = Set<URL>()
            let variants = video.array("bit_rate").compactMap { $0 as? [String: Any] }
                .sorted { $0.integer("bit_rate") > $1.integer("bit_rate") }
            var seenIDs = Set<String>()
            for variant in variants {
                let address = variant.object("play_addr")
                guard variant.integer("is_h265") == 0, variant.integer("is_bytevc1") == 0,
                      let url = firstMediaURL(address["url_list"]), seen.insert(url).inserted else { continue }
                let shortSide = min(address.integer("width"), address.integer("height"))
                let identity = "video-\(variant.string("gear_name") ?? "avc")-\(address.integer("width"))x\(address.integer("height"))-\(variant.integer("bit_rate"))"
                guard seenIDs.insert(identity).inserted else { continue }
                assets.append(MediaAsset(id: identity, url: url, kind: .video,
                                         label: shortSide > 0 ? "视频 · \(shortSide)p" : "公开视频",
                                         fileExtension: "mp4", headers: headers))
            }
            if assets.isEmpty, video.integer("is_h265") == 0, video.integer("is_bytevc1") == 0,
               let url = firstMediaURL(video.object("play_addr")["url_list"]) {
                assets.append(MediaAsset(id: "video", url: url, kind: .video, label: "公开视频", fileExtension: "mp4", headers: headers))
            }
        }
        guard !assets.isEmpty else { throw unavailable }
        let thumbnail = firstMediaURL(video.object("cover")["url_list"])
            ?? firstMediaURL(video.object("origin_cover")["url_list"])
            ?? assets.first(where: { $0.kind == .image })?.url
        return MediaPreview(id: id, sourceURL: sourceURL, platform: .douyin, title: title,
                            uploader: uploader, thumbnailURL: thumbnail, assets: assets)
    }

    struct BilibiliMetadata {
        let id: String
        let cid: String
        let title: String
        let uploader: String?
        let thumbnail: URL?
    }

    static func bilibiliMetadata(_ data: Data, bvid: String, page: Int) throws -> BilibiliMetadata {
        let payload = try apiData(data)
        guard payload.string("bvid") == bvid else { throw FlowFrameError.message("平台返回的作品编号与链接不一致。") }
        let rights = payload.object("rights")
        guard rights.integer("pay") == 0, rights.integer("area_limit") == 0,
              payload.integer("is_upower_exclusive") == 0, payload.integer("is_upower_play") == 0 else { throw unavailable }
        let pages = payload.array("pages").compactMap { $0 as? [String: Any] }
        let selected: [String: Any]
        if pages.isEmpty, page == 1 { selected = payload }
        else if let candidate = pages.first(where: { $0.integer("page") == page }) { selected = candidate }
        else { throw FlowFrameError.message("链接指定的分 P 不存在。") }
        guard let cid = selected.string("cid"), cid.matches(#"^[0-9]{1,24}$"#) else { throw unavailable }
        let title = payload.string("title")?.nonempty ?? "未命名哔哩哔哩视频"
        let part = selected.string("part")?.nonempty
        return BilibiliMetadata(id: bvid, cid: cid, title: pages.count > 1 ? title + " · P\(page)" + (part.map { " " + $0 } ?? "") : title,
                                uploader: payload.object("owner").string("name"), thumbnail: MediaURLPolicy.normalized(payload.string("pic")))
    }

    static func bilibiliPlayback(_ data: Data, metadata: BilibiliMetadata, sourceURL: URL) throws -> MediaPreview {
        let payload = try apiData(data)
        guard payload.integer("is_preview") == 0, payload.integer("need_login") == 0,
              payload.integer("is_drm") == 0 else { throw unavailable }
        let headers = ["Referer": "https://www.bilibili.com/", "User-Agent": PublicHTTP.userAgent]
        let dash = payload.object("dash")
        var assets: [MediaAsset] = []
        let audios = dash.array("audio").compactMap { $0 as? [String: Any] }
            .filter { ($0.string("codecs") ?? "").lowercased().hasPrefix("mp4a") }
            .sorted { $0.integer("bandwidth") > $1.integer("bandwidth") }
        let audio = audios.compactMap(streamURL).first
        let videos = dash.array("video").compactMap { $0 as? [String: Any] }
            .filter { ($0.string("codecs") ?? "").lowercased().hasPrefix("avc1") && $0.integer("drm_tech_type") == 0 }
            .sorted { $0.integer("height") == $1.integer("height") ? $0.integer("bandwidth") > $1.integer("bandwidth") : $0.integer("height") > $1.integer("height") }
        var seen = Set<URL>()
        var seenIDs = Set<String>()
        for video in videos {
            guard let url = streamURL(video), seen.insert(url).inserted else { continue }
            // A nonempty audio manifest that contains no supported AAC is not silently discarded.
            guard dash.array("audio").isEmpty || audio != nil else { continue }
            let height = video.integer("height")
            let identity = "dash-\(video.string("id") ?? "unknown")-\(video.integer("width"))x\(height)-\(video.string("codecs") ?? "avc1")"
            guard seenIDs.insert(identity).inserted else { continue }
            assets.append(MediaAsset(id: identity, url: url, kind: .video,
                                     label: height > 0 ? "视频 · \(height)p" : "公开视频", fileExtension: "mp4", headers: headers,
                                     companionAudioURL: audio))
        }
        if let audio = audio {
            assets.append(MediaAsset(id: "dash-audio", url: audio, kind: .audio, label: "仅音频 · AAC", fileExtension: "m4a", headers: headers))
        }
        if !assets.contains(where: { $0.kind == .video }) {
            let segments = payload.array("durl")
            if segments.count > 1 { throw mixed }
            if let segment = segments.first as? [String: Any], let url = streamURL(segment) {
                let format = payload.string("format")?.lowercased() ?? ""
                guard !format.contains("flv"), !url.path.lowercased().hasSuffix(".flv") else { throw mixed }
                assets.insert(MediaAsset(id: "progressive", url: url, kind: .video, label: "公开视频", fileExtension: "mp4", headers: headers), at: 0)
            }
        }
        guard assets.contains(where: { $0.kind == .video }) else { throw FlowFrameError.message("未找到免登录可用的 H.264 视频与 AAC 音频。") }
        return MediaPreview(id: metadata.id, sourceURL: sourceURL, platform: .bilibili, title: metadata.title,
                            uploader: metadata.uploader, thumbnailURL: metadata.thumbnail, assets: assets)
    }

    private static func apiData(_ data: Data) throws -> [String: Any] {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              root["code"] != nil, root.integer("code") == 0, let payload = root["data"] as? [String: Any] else { throw unavailable }
        return payload
    }

    private static func streamURL(_ object: [String: Any]) -> URL? {
        for key in ["baseUrl", "base_url", "url"] {
            if let url = MediaURLPolicy.normalized(object.string(key)) { return url }
        }
        return firstMediaURL(object["backupUrl"]) ?? firstMediaURL(object["backup_url"])
    }

    private static func firstMediaURL(_ value: Any?) -> URL? {
        (value as? [String])?.compactMap { MediaURLPolicy.normalized($0) }.first
    }

    private static func imageExtension(_ url: URL) -> String {
        let ext = url.pathExtension.lowercased()
        return ["png", "webp", "jpeg", "jpg"].contains(ext) ? ext : "jpg"
    }

    static func embeddedObject(_ html: String, marker: String) -> Data? {
        guard let markerRange = html.range(of: marker),
              let start = html[markerRange.upperBound...].firstIndex(of: "{"),
              html.distance(from: markerRange.upperBound, to: start) < 160 else { return nil }
        var depth = 0, quoted = false, escaped = false
        for index in html[start...].indices {
            let character = html[index]
            if quoted {
                if escaped { escaped = false }
                else if character == "\\" { escaped = true }
                else if character == "\"" { quoted = false }
            } else if character == "\"" { quoted = true }
            else if character == "{" { depth += 1 }
            else if character == "}" {
                depth -= 1
                if depth == 0 { return String(html[start...index]).data(using: .utf8) }
            }
        }
        return nil
    }
}

extension Dictionary where Key == String, Value == Any {
    func object(_ key: String) -> [String: Any] { self[key] as? [String: Any] ?? [:] }
    func array(_ key: String) -> [Any] { self[key] as? [Any] ?? [] }
    func string(_ key: String) -> String? {
        if let string = self[key] as? String { return string }
        if let number = self[key] as? NSNumber { return number.stringValue }
        return nil
    }
    func integer(_ key: String) -> Int { (self[key] as? NSNumber)?.intValue ?? Int(self[key] as? String ?? "") ?? 0 }
}

private extension String { var nonempty: String? { isEmpty ? nil : self } }
