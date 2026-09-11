# FlowFrame 1.2.0 accepted implementation plan

Base: `8ce09e5437796a028a7c0674972981788065dd19` (1.1.2).

Deliver a local signed arm64 APK and source; do not publish or push. Preserve package identity, original release signing key, Android 7+ support, task history and settings. Version 1.2.0 / code 6.

## Product

- Refine the purple/teal native Compose UI across home, preview, tasks and settings. Real artwork, typography, responsive layouts, accessible controls, light/dark themes and system back navigation.
- Every visible control must perform its stated action. Gallery browsing and selection, actual format details, directory selection with persistent grants, readable diagnostics and license/about views. Remove placeholder credentials and pause/resume controls.
- Retain Douyin and Bilibili, implement Xiaohongshu, Weibo and Kuaishou adapters for anonymous public single videos and pure image galleries. Reject mixed posts, playlists, live and restricted content explicitly. Kuaishou may remain incomplete for the first APK if actual anonymous download verification fails; report and continue adaptation, never advertise unverified capabilities.
- Keep necessary share parameters and precise host validation; upgrade known official HTTP short links to HTTPS before requests. Platform + media ID identifies previews.
- Gallery output is selected original-order images, available audio, or composed MP4. Support real image MIME types.

## Reliability

- Display actual transfer metrics and network state. Correct cancellation/retry/publication races. Wi-Fi-only governs queued and new tasks; network loss waits instead of presenting user cancellation. Concurrency changes govern subsequent starts.
- Keep high-frequency progress off disk and all file I/O off the main thread. New persistent fields have backwards-compatible defaults.
- System directory picker is actionable; default destination remains current media directories. Lost grants produce a recoverable error, not a silent destination change. Deleting a record preserves media.

## Validation seams agreed with the user

URL normalization/validation, platform adapters and identity, image selection, task state transitions, history migration, and user-visible Compose interactions. Use reproducible failures before fixes at these seams. Run JVM tests, release lint, debug/release builds, and two-axis code review.

ADB test existing API36 x86_64 AVD and additional API24/29/35 images. Preserve existing AVD data. Test release-minified runtime, actual parse/download/decodable output, all controls, denied permissions, offline/reconnect, full storage, expired resources, rotation/background/process recreation. Verify upgrade data with the old certificate. Check arm64 native libraries, signing, 16 KiB alignment and SHA256; distinguish emulator results from untested arm64 hardware.

Report passed, blocked and untested capability/platform/API combinations separately. Supply APK, checksum, certificate fingerprint, source, build instructions, screenshots and test report.
