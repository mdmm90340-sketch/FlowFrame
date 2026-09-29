import Foundation

public enum MediaPlatform: String, Codable, Sendable {
    case douyin, bilibili

    public var title: String { self == .douyin ? "抖音" : "哔哩哔哩" }
}

public enum MediaKind: String, Codable, Sendable {
    case video, image, audio
}

public struct MediaAsset: Identifiable, Codable, Hashable, Sendable {
    public let id: String
    public let url: URL
    public let kind: MediaKind
    public let label: String
    public let fileExtension: String
    public let headers: [String: String]
    public let companionAudioURL: URL?

    public init(id: String, url: URL, kind: MediaKind, label: String,
                fileExtension: String, headers: [String: String] = [:],
                companionAudioURL: URL? = nil) {
        self.id = id
        self.url = url
        self.kind = kind
        self.label = label
        self.fileExtension = fileExtension
        self.headers = headers
        self.companionAudioURL = companionAudioURL
    }
}

public struct MediaPreview: Identifiable, Sendable {
    public let id: String
    public let sourceURL: URL
    public let platform: MediaPlatform
    public let title: String
    public let uploader: String?
    public let thumbnailURL: URL?
    public let assets: [MediaAsset]

    public init(id: String, sourceURL: URL, platform: MediaPlatform, title: String,
                uploader: String? = nil, thumbnailURL: URL? = nil, assets: [MediaAsset]) {
        self.id = id
        self.sourceURL = sourceURL
        self.platform = platform
        self.title = title
        self.uploader = uploader
        self.thumbnailURL = thumbnailURL
        self.assets = assets
    }
}

public enum FlowFrameError: LocalizedError, Equatable {
    case message(String)
    public var errorDescription: String? {
        switch self { case .message(let text): return text }
    }
}
