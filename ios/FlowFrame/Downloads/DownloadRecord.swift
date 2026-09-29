import Foundation
import FlowFrameCore

enum DownloadStatus: String, Codable {
    case queued, resolving, downloading, merging, completed, failed, cancelled

    var title: String {
        switch self {
        case .queued: return "等待下载"
        case .resolving: return "刷新下载地址"
        case .downloading: return "正在下载"
        case .merging: return "正在合并音视频"
        case .completed: return "已完成"
        case .failed: return "下载失败"
        case .cancelled: return "已取消"
        }
    }

    var isPending: Bool { [.queued, .resolving, .downloading, .merging].contains(self) }
}

struct DownloadRecord: Identifiable, Codable {
    let id: UUID
    let title: String
    let sourceText: String
    let assetID: String
    let assetLabel: String
    let kind: MediaKind
    let platform: MediaPlatform
    let createdAt: Date
    var status: DownloadStatus
    var progress: Double?
    var detail: String?
    var fileName: String?

    init(preview: MediaPreview, asset: MediaAsset, sourceText: String) {
        id = UUID()
        title = preview.title
        self.sourceText = sourceText
        assetID = asset.id
        assetLabel = asset.label
        kind = asset.kind
        platform = preview.platform
        createdAt = Date()
        status = .queued
    }
}

enum DownloadFailure: LocalizedError {
    case message(String)
    var errorDescription: String? {
        switch self { case .message(let value): return value }
    }
}
