import Foundation
import XCTest
@testable import FlowFrameCore

final class ShareLinkParserTests: XCTestCase {
    func testShareTextAndOfficialHTTPShortLinkAreNormalized() throws {
        let result = try ShareLinkParser.parse("复制打开抖音 https://v.douyin.com/AbC_123/。看看这个作品")
        XCTAssertEqual(result.platform, .douyin)
        XCTAssertEqual(result.url.absoluteString, "https://v.douyin.com/AbC_123/")
        XCTAssertEqual(try ShareLinkParser.parse("http://b23.tv/abc").url.absoluteString, "https://b23.tv/abc")
        XCTAssertEqual(try ShareLinkParser.parse("https://WWW.BILIBILI.COM.:443/video/BV17fTF6EEAc?p=2#share").url.absoluteString,
                       "https://www.bilibili.com/video/BV17fTF6EEAc?p=2")
    }

    func testRejectsAmbiguousAndOversizedInput() {
        XCTAssertThrowsError(try ShareLinkParser.parse("https://b23.tv/one https://v.douyin.com/two/"))
        XCTAssertThrowsError(try ShareLinkParser.parse(String(repeating: "文", count: 6000) + "https://b23.tv/one"))
        XCTAssertThrowsError(try ShareLinkParser.parse("没有链接"))
    }

    func testDuplicateLinkIsNotAmbiguous() throws {
        XCTAssertEqual(try ShareLinkParser.parse("https://b23.tv/abc https://b23.tv/abc").url.host, "b23.tv")
    }

    func testRejectsConfusableOrUnsafeURLs() {
        let values = [
            "https://bilibili.com.evil.example/video/BV17fTF6EEAc", "https://user:pass@www.bilibili.com/video/BV17fTF6EEAc",
            "https://www.bilibili.com:444/video/BV17fTF6EEAc", "https://www.bilibili.com:/video/BV17fTF6EEAc",
            "https://www.bilibili.com:0443/video/BV17fTF6EEAc", "https://foo.bilibili.com/video/BV17fTF6EEAc",
            "http://www.bilibili.com/video/BV17fTF6EEAc", "https://127.0.0.1/video/BV17fTF6EEAc",
            "https://www.bilibili.com/video/BV1", "https://www.bilibili.com/video/BV17fTF6EEAc/extra",
            "https://v.douyin.com/abc%2Fdef", "https://www.douyin.com/video/not-numeric",
            "https://www.bilibili.com%2eevil.example/video/BV17fTF6EEAc", "https://b23.tv/a https://evil.example/b"
        ]
        for value in values { XCTAssertThrowsError(try ShareLinkParser.parse(value), value) }
    }

    func testMediaAndRedirectPoliciesHaveLabelBoundaries() {
        for value in ["https://v3.douyinvod.com/a.mp4", "https://p3.douyinpic.com/a.webp", "https://xy1.mcdn.bilivideo.cn/a.m4s"] {
            XCTAssertTrue(MediaURLPolicy.validate(URL(string: value)!), value)
        }
        for value in ["http://v3.douyinvod.com/a", "https://v3.douyinvod.com.evil.example/a", "https://evildouyinvod.com/a",
                      "https://127.0.0.1/a", "https://192.168.1.1/a", "https://[::1]/a", "https://localhost/a",
                      "https://user@v3.douyinvod.com/a", "https://v3.douyinvod.com:8443/a", "https://unknown.example/a"] {
            XCTAssertFalse(MediaURLPolicy.validate(URL(string: value)!), value)
        }
        XCTAssertFalse(PageURLPolicy.validate(URL(string: "https://www.bilibili.com/")!, platform: .douyin))
        XCTAssertFalse(PageURLPolicy.validate(URL(string: "http://www.douyin.com/")!, platform: .douyin))
        XCTAssertFalse(PageURLPolicy.validate(URL(string: "https://evil.douyin.com/")!, platform: .douyin))
    }
}

final class PublicMediaParserTests: XCTestCase {
    private let douyinURL = URL(string: "https://www.douyin.com/video/123456789")!
    private let biliURL = URL(string: "https://www.bilibili.com/video/BV17fTF6EEAc")!

    private func json(_ value: Any) throws -> Data { try JSONSerialization.data(withJSONObject: value, options: .sortedKeys) }
    private func video(_ id: String = "123456789") -> [String: Any] {
        ["aweme_id": id, "desc": "测试标题 {含括号}", "author": ["nickname": "作者"],
         "video": ["play_addr": ["url_list": ["https://v3.douyinvod.com/video.mp4"]],
                   "cover": ["url_list": ["https://p3.douyinpic.com/cover.jpg"]]]]
    }
    private func metadata() throws -> PublicMediaParser.BilibiliMetadata {
        try PublicMediaParser.bilibiliMetadata(json(["code": 0, "data": ["bvid": "BV17fTF6EEAc", "title": "测试视频", "cid": 100]]), bvid: "BV17fTF6EEAc", page: 1)
    }
    private func dashVideo(quality: Int, height: Int, codec: String = "avc1.640028") -> [String: Any] {
        ["id": quality, "height": height, "width": 1920, "codecs": codec, "bandwidth": height * 1000,
         "baseUrl": "https://unknown.example/\(quality).m4s",
         "backupUrl": ["https://cdn.bilivideo.com/\(quality).m4s"]]
    }
    private func dash(_ videos: [[String: Any]]) throws -> Data {
        try json(["code": 0, "data": ["dash": ["video": videos,
             "audio": [["id": 30280, "codecs": "mp4a.40.2", "bandwidth": 192000, "baseUrl": "https://cdn.bilivideo.com/audio.m4s"]]]]])
    }

    func testDouyinSelectsExactIDAndIgnoresRecommendedNeighbor() throws {
        let data = try json(["recommendations": [video("999999999")], "loaderData": ["detail": video()]])
        let result = try PublicMediaParser.douyinJSON(data, id: "123456789", sourceURL: douyinURL)
        XCTAssertEqual(result.id, "123456789")
        XCTAssertEqual(result.assets.first?.kind, .video)
        XCTAssertEqual(result.uploader, "作者")
        XCTAssertThrowsError(try PublicMediaParser.douyinJSON(json(["recommendations": [video("999999999")]]), id: "123456789", sourceURL: douyinURL))
    }

    func testDouyinEmbeddedJSONHandlesQuotedBraces() throws {
        let data = try json(["loaderData": ["video_(id)/page": ["videoInfoRes": ["item_list": [video()]]]]])
        let html = "<script>window._ROUTER_DATA = " + String(decoding: data, as: UTF8.self) + ";</script>"
        XCTAssertEqual(try PublicMediaParser.douyinHTML(html, id: "123456789", sourceURL: douyinURL).title, "测试标题 {含括号}")
        let encoded = String(decoding: data, as: UTF8.self).addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!
        XCTAssertEqual(try PublicMediaParser.douyinHTML("<script id=\"RENDER_DATA\">\(encoded)</script>", id: "123456789", sourceURL: douyinURL).assets.count, 1)
    }

    func testDouyinGalleryPreservesOrderAndSeparateOriginalAudio() throws {
        let detail: [String: Any] = ["aweme_id": "123456789", "images": [
            ["url_list": ["https://p3.douyinpic.com/first.webp"]],
            ["url_list": ["https://p3.douyinpic.com/second.jpg"]]],
            "music": ["play_url": ["url_list": ["https://sf3-cdn-tos.douyinstatic.com/audio.mp3", "https://sf3.pstatp.com/audio.mp3"]]]]
        let result = try PublicMediaParser.douyinJSON(json(["aweme_detail": detail]), id: "123456789", sourceURL: douyinURL)
        XCTAssertEqual(result.assets.map(\.kind), [.image, .image, .audio])
        XCTAssertEqual(result.assets[0].url.lastPathComponent, "first.webp")
        XCTAssertEqual(result.assets[0].fileExtension, "webp")
        XCTAssertEqual(result.assets[1].url.lastPathComponent, "second.jpg")
        XCTAssertEqual(result.assets[2].url.host, "sf3.pstatp.com")
    }

    func testDouyinRejectsIncompleteUnsafeRestrictedAndMixedPosts() throws {
        var restricted = video(); restricted["status"] = ["is_private": 1]
        var live = video(); live["is_live"] = true
        var mixed = video(); mixed["images"] = [["url_list": ["https://p3.douyinpic.com/image.jpg"]]]
        let empty: [String: Any] = ["aweme_id": "123456789", "images": []]
        let unsafe: [String: Any] = ["aweme_id": "123456789", "images": [["url_list": ["https://127.0.0.1/private"]]]]
        let partial: [String: Any] = ["aweme_id": "123456789", "images": [["url_list": ["https://p3.douyinpic.com/a.jpg"]], ["url_list": []]]]
        for detail in [restricted, live, mixed, empty, unsafe, partial] {
            XCTAssertThrowsError(try PublicMediaParser.douyinJSON(json(["aweme_detail": detail]), id: "123456789", sourceURL: douyinURL))
        }
        XCTAssertThrowsError(try PublicMediaParser.douyinHTML("<title>安全验证</title>", id: "123456789", sourceURL: douyinURL))
    }

    func testBilibiliMetadataBindsBVAndSelectedPage() throws {
        let data = try json(["code": 0, "data": ["bvid": "BV17fTF6EEAc", "title": "作品", "pages": [
            ["page": 1, "cid": 100, "part": "第一部分"], ["page": 2, "cid": 200, "part": "第二部分"]]]])
        let selected = try PublicMediaParser.bilibiliMetadata(data, bvid: "BV17fTF6EEAc", page: 2)
        XCTAssertEqual(selected.cid, "200")
        XCTAssertTrue(selected.title.contains("P2"))
        XCTAssertThrowsError(try PublicMediaParser.bilibiliMetadata(data, bvid: "BV1different1", page: 1))
        XCTAssertThrowsError(try PublicMediaParser.bilibiliMetadata(data, bvid: "BV17fTF6EEAc", page: 3))
        let paid = try json(["code": 0, "data": ["bvid": "BV17fTF6EEAc", "cid": 100, "rights": ["pay": 1]]])
        XCTAssertThrowsError(try PublicMediaParser.bilibiliMetadata(paid, bvid: "BV17fTF6EEAc", page: 1))
    }

    func testBilibiliDASHUsesAVCAndAACAndSafeBackup() throws {
        let result = try PublicMediaParser.bilibiliPlayback(dash([
            dashVideo(quality: 80, height: 1080), dashVideo(quality: 64, height: 720), dashVideo(quality: 120, height: 2160, codec: "hev1.1.6.L120")
        ]), metadata: metadata(), sourceURL: biliURL)
        XCTAssertEqual(result.assets.map(\.kind), [.video, .video, .audio])
        XCTAssertEqual(result.assets.first?.url.host, "cdn.bilivideo.com")
        XCTAssertEqual(result.assets.first?.companionAudioURL?.lastPathComponent, "audio.m4s")
        XCTAssertEqual(result.assets.first?.headers["Referer"], "https://www.bilibili.com/")
    }

    func testAssetIdentitySurvivesHigherQualityDisappearing() throws {
        let low = dashVideo(quality: 64, height: 720)
        let high = dashVideo(quality: 80, height: 1080)
        let before = try PublicMediaParser.bilibiliPlayback(dash([high, low]), metadata: metadata(), sourceURL: biliURL)
        let after = try PublicMediaParser.bilibiliPlayback(dash([low]), metadata: metadata(), sourceURL: biliURL)
        XCTAssertEqual(before.assets[1].id, after.assets[0].id)
        XCTAssertNotEqual(before.assets[0].id, after.assets[0].id)
    }

    func testBilibiliRejectsPreviewSegmentsMissingContentAndUnsupportedCodec() throws {
        let payloads: [[String: Any]] = [
            ["code": -404, "data": [:]], ["code": 0, "data": [:]],
            ["code": 0, "data": ["is_preview": 1, "durl": [["url": "https://cdn.bilivideo.com/a.mp4"]]]],
            ["code": 0, "data": ["durl": [["url": "https://cdn.bilivideo.com/a.mp4"], ["url": "https://cdn.bilivideo.com/b.mp4"]]]],
            ["code": 0, "data": ["format": "flv", "durl": [["url": "https://cdn.bilivideo.com/a.flv"]]]]
        ]
        for payload in payloads {
            XCTAssertThrowsError(try PublicMediaParser.bilibiliPlayback(json(payload), metadata: metadata(), sourceURL: biliURL))
        }
        XCTAssertThrowsError(try PublicMediaParser.bilibiliPlayback(dash([dashVideo(quality: 80, height: 1080, codec: "av01.0.08M.08")]), metadata: metadata(), sourceURL: biliURL))
    }

    func testBilibiliSingleMP4CanBeSavedWithoutCompanion() throws {
        let result = try PublicMediaParser.bilibiliPlayback(json(["code": 0, "data": ["format": "mp4", "durl": [["url": "http://cdn.bilivideo.com/a.mp4"]]]]), metadata: metadata(), sourceURL: biliURL)
        XCTAssertEqual(result.assets.count, 1)
        XCTAssertEqual(result.assets[0].url.scheme, "https")
        XCTAssertNil(result.assets[0].companionAudioURL)
    }
}
