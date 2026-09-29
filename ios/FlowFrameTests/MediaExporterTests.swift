import AVFoundation
import CoreVideo
import Foundation
import XCTest
@testable import FlowFrame

final class MediaExporterTests: XCTestCase {
    func testMergeProducesPlayableVideoAndAudioTracks() async throws {
        let directory = try makeTemporaryDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let video = directory.appendingPathComponent("video.mp4")
        let audio = directory.appendingPathComponent("audio.m4a")
        let destination = directory.appendingPathComponent("merged.mp4")
        try await writeVideo(to: video)
        try writeAudio(to: audio)

        let output = try await MediaExporter.merge(video: video, audio: audio, destination: destination)

        XCTAssertEqual(output, destination)
        XCTAssertTrue(FileManager.default.fileExists(atPath: output.path))
        let asset = AVURLAsset(url: output)
        let videoTracks = try await asset.loadTracks(withMediaType: .video)
        let audioTracks = try await asset.loadTracks(withMediaType: .audio)
        let duration = try await asset.load(.duration)
        let playable = try await asset.load(.isPlayable)
        XCTAssertEqual(videoTracks.count, 1)
        XCTAssertEqual(audioTracks.count, 1)
        XCTAssertTrue(playable)
        XCTAssertTrue(duration.isNumeric)
        XCTAssertGreaterThan(duration.seconds, 0.5)
        try await MediaExporter.validate(output, kind: .video)
        try await MediaExporter.validate(output, kind: .audio)
    }

    func testValidateRejectsHTMLAndJSONDisguisedAsVideo() async throws {
        let directory = try makeTemporaryDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }

        for (name, body) in [
            ("html.mp4", "<html><body>Access denied</body></html>"),
            ("json.mp4", "{\"error\":\"expired link\"}")
        ] {
            let file = directory.appendingPathComponent(name)
            try Data(body.utf8).write(to: file)
            do {
                try await MediaExporter.validate(file, kind: .video)
                XCTFail("The exporter accepted non-media data from \(name).")
            } catch {
                // Any validation error is valid: AVFoundation controls its error domain.
            }
        }
    }

    func testValidateRejectsInvalidImageAndVideoWithoutAudio() async throws {
        let directory = try makeTemporaryDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let image = directory.appendingPathComponent("invalid.jpg")
        try Data("not an image".utf8).write(to: image)
        do {
            try await MediaExporter.validate(image, kind: .image)
            XCTFail("The exporter accepted invalid image bytes.")
        } catch {}

        let video = directory.appendingPathComponent("silent.mp4")
        try await writeVideo(to: video)
        try await MediaExporter.validate(video, kind: .video)
        do {
            try await MediaExporter.validate(video, kind: .audio)
            XCTFail("The exporter accepted a video-only asset as audio.")
        } catch {}
    }

    func testCancelledMergeDoesNotProduceAnOutput() async throws {
        let directory = try makeTemporaryDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let video = directory.appendingPathComponent("video.mp4")
        let audio = directory.appendingPathComponent("audio.m4a")
        let destination = directory.appendingPathComponent("cancelled.mp4")
        try await writeVideo(to: video)
        try writeAudio(to: audio)

        // Cancel deterministically before starting, rather than racing a tiny export.
        let operation = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return try await MediaExporter.merge(video: video, audio: audio, destination: destination)
        }
        do {
            _ = try await operation.value
            XCTFail("A cancelled merge reported success.")
        } catch {
            XCTAssertTrue(operation.isCancelled)
        }
        XCTAssertFalse(FileManager.default.fileExists(atPath: destination.path))
    }

    private func makeTemporaryDirectory() throws -> URL {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("FlowFrame-MediaExporterTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    private func writeVideo(to url: URL) async throws {
        let writer = try AVAssetWriter(outputURL: url, fileType: .mp4)
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: 64,
            AVVideoHeightKey: 64
        ])
        input.expectsMediaDataInRealTime = false
        let attributes: [String: Any] = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: 64,
            kCVPixelBufferHeightKey as String: 64,
            kCVPixelBufferIOSurfacePropertiesKey as String: [:]
        ]
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input,
                                                          sourcePixelBufferAttributes: attributes)
        guard writer.canAdd(input) else { throw FixtureError.failed("Cannot add the H.264 fixture input.") }
        writer.add(input)
        guard writer.startWriting() else { throw writer.error ?? FixtureError.failed("Cannot start fixture writing.") }
        defer { if writer.status == .writing { writer.cancelWriting() } }
        writer.startSession(atSourceTime: .zero)

        let deadline = Date().addingTimeInterval(15)
        for frame in 0..<30 {
            while !input.isReadyForMoreMediaData {
                guard writer.status == .writing, Date() < deadline else {
                    throw writer.error ?? FixtureError.failed("Video fixture encoder did not become ready.")
                }
                try await Task.sleep(nanoseconds: 1_000_000)
            }
            var buffer: CVPixelBuffer?
            let status = CVPixelBufferCreate(kCFAllocatorDefault, 64, 64, kCVPixelFormatType_32BGRA,
                                            attributes as CFDictionary, &buffer)
            guard status == kCVReturnSuccess, let buffer else {
                throw FixtureError.failed("Cannot allocate a video fixture frame.")
            }
            CVPixelBufferLockBaseAddress(buffer, [])
            guard let base = CVPixelBufferGetBaseAddress(buffer) else {
                CVPixelBufferUnlockBaseAddress(buffer, [])
                throw FixtureError.failed("Video fixture frame has no pixel storage.")
            }
            base.assumingMemoryBound(to: UInt8.self).initialize(repeating: UInt8(64 + frame),
                count: CVPixelBufferGetBytesPerRow(buffer) * CVPixelBufferGetHeight(buffer))
            CVPixelBufferUnlockBaseAddress(buffer, [])
            guard adaptor.append(buffer, withPresentationTime: CMTime(value: Int64(frame), timescale: 30)) else {
                throw writer.error ?? FixtureError.failed("Cannot append a video fixture frame.")
            }
        }
        writer.endSession(atSourceTime: CMTime(value: 30, timescale: 30))
        input.markAsFinished()
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            writer.finishWriting { continuation.resume() }
        }
        guard writer.status == .completed else {
            throw writer.error ?? FixtureError.failed("Video fixture writing did not complete.")
        }
    }

    private func writeAudio(to url: URL) throws {
        // AVAudioFile encodes generated PCM into AAC without an audio session or microphone.
        let file = try AVAudioFile(forWriting: url, settings: [
            AVFormatIDKey: kAudioFormatMPEG4AAC,
            AVSampleRateKey: 44_100,
            AVNumberOfChannelsKey: 1,
            AVEncoderBitRateKey: 64_000
        ], commonFormat: .pcmFormatFloat32, interleaved: false)
        guard let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat, frameCapacity: 44_100),
              let samples = buffer.floatChannelData?[0] else {
            throw FixtureError.failed("Cannot allocate the audio fixture buffer.")
        }
        buffer.frameLength = 44_100
        for frame in 0..<Int(buffer.frameLength) {
            samples[frame] = Float(sin(2 * Double.pi * 440 * Double(frame) / 44_100)) * 0.1
        }
        try file.write(from: buffer)
    }

    private enum FixtureError: Error {
        case failed(String)
    }
}
