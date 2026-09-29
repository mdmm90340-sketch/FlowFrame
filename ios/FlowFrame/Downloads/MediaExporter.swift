import AVFoundation
import Foundation
import UIKit
import FlowFrameCore

enum MediaExporter {
    static func validate(_ file: URL, kind: MediaKind) async throws {
        switch kind {
        case .image:
            _ = try ImageFile.thumbnail(at: file, maximumPixelSize: 512)
        case .audio, .video:
            let asset = AVURLAsset(url: file)
            let tracks = try await asset.loadTracks(withMediaType: kind == .video ? .video : .audio)
            let duration = try await asset.load(.duration)
            let playable = try await asset.load(.isPlayable)
            guard !tracks.isEmpty, playable, duration.isNumeric, duration.seconds > 0 else {
                throw DownloadFailure.message("下载内容缺少有效的媒体轨道，请重新解析。")
            }
        }
    }

    static func merge(video: URL, audio: URL, destination: URL) async throws -> URL {
        try Task.checkCancellation()
        let videoAsset = AVURLAsset(url: video)
        let audioAsset = AVURLAsset(url: audio)
        guard let videoTrack = try await videoAsset.loadTracks(withMediaType: .video).first,
              let audioTrack = try await audioAsset.loadTracks(withMediaType: .audio).first else {
            throw DownloadFailure.message("音视频轨道不完整，无法合并。")
        }
        let videoDuration = try await videoAsset.load(.duration)
        let audioDuration = try await audioAsset.load(.duration)
        guard videoDuration.isNumeric, audioDuration.isNumeric,
              videoDuration.seconds > 0, audioDuration.seconds > 0 else {
            throw DownloadFailure.message("音视频时长无效，无法合并。")
        }
        let composition = AVMutableComposition()
        guard let newVideo = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid),
              let newAudio = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) else {
            throw DownloadFailure.message("iOS 无法创建合并任务，请稍后重试。")
        }
        try newVideo.insertTimeRange(CMTimeRange(start: .zero, duration: videoDuration), of: videoTrack, at: .zero)
        try newAudio.insertTimeRange(CMTimeRange(start: .zero, duration: CMTimeMinimum(videoDuration, audioDuration)), of: audioTrack, at: .zero)
        newVideo.preferredTransform = try await videoTrack.load(.preferredTransform)
        guard let exporter = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetPassthrough),
              exporter.supportedFileTypes.contains(.mp4) else {
            throw DownloadFailure.message("此音视频编码无法导出为 MP4，请选择其他画质。")
        }
        exporter.outputURL = destination
        exporter.outputFileType = .mp4
        exporter.shouldOptimizeForNetworkUse = true
        try await withTaskCancellationHandler {
            try Task.checkCancellation()
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                exporter.exportAsynchronously {
                    switch exporter.status {
                    case .completed: continuation.resume()
                    case .cancelled: continuation.resume(throwing: CancellationError())
                    default:
                        continuation.resume(throwing: DownloadFailure.message("iOS 无法合并此媒体编码，请选择其他画质。"))
                    }
                }
                // Cancellation before exportAsynchronously may have been ignored by AVFoundation.
                // Re-check after starting; later cancellation is handled by onCancel.
                if Task.isCancelled { exporter.cancelExport() }
            }
        } onCancel: {
            exporter.cancelExport()
        }
        try Task.checkCancellation()
        let result = AVURLAsset(url: destination)
        let videos = try await result.loadTracks(withMediaType: .video)
        let audios = try await result.loadTracks(withMediaType: .audio)
        guard !videos.isEmpty, !audios.isEmpty else {
            throw DownloadFailure.message("合并后缺少音轨或画面，文件未保存。")
        }
        try await validate(destination, kind: .video)
        return destination
    }
}
