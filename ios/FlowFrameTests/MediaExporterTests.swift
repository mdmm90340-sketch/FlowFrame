import AVFoundation
import Foundation
import XCTest
@testable import FlowFrame

final class MediaExporterTests: XCTestCase {
    func testMergeProducesPlayableVideoAndAudioTracks() async throws {
        let directory = try makeTemporaryDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let video = try fixtureURL("fixture-video-h264", fileExtension: "mp4")
        let audio = try fixtureURL("fixture-audio-aac", fileExtension: "m4a")
        let destination = directory.appendingPathComponent("merged.mp4")

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

        let video = try fixtureURL("fixture-video-h264", fileExtension: "mp4")
        try await MediaExporter.validate(video, kind: .video)
        do {
            try await MediaExporter.validate(video, kind: .audio)
            XCTFail("The exporter accepted a video-only asset as audio.")
        } catch {}
    }

    func testCancelledMergeDoesNotProduceAnOutput() async throws {
        let directory = try makeTemporaryDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let video = try fixtureURL("fixture-video-h264", fileExtension: "mp4")
        let audio = try fixtureURL("fixture-audio-aac", fileExtension: "m4a")
        let destination = directory.appendingPathComponent("cancelled.mp4")

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

    private func fixtureURL(_ name: String, fileExtension: String) throws -> URL {
        // Test the actual exporter with fixed media, independently of simulator
        // encoder availability and cold-start scheduling. Missing resources fail.
        let bundle = Bundle(for: MediaExporterTests.self)
        return try XCTUnwrap(
            bundle.url(forResource: name, withExtension: fileExtension),
            "Missing bundled media fixture: \(name).\(fileExtension)"
        )
    }
}
