import SwiftUI
import FlowFrameCore
import QuickLook

struct DownloadsView: View {
    @EnvironmentObject private var downloads: DownloadStore
    @State private var presentation: FilePresentation?

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                NoticeCard(icon: "iphone", text: "下载时请保持 App 在前台。任务按顺序执行，后台中断后可重试；重试会刷新链接并从头下载。")
                if let warning = downloads.storageWarning {
                    NoticeCard(icon: "exclamationmark.triangle", text: warning)
                }
                if downloads.records.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "tray.and.arrow.down")
                            .font(.system(size: 48, weight: .light)).foregroundStyle(Brand.gradient)
                        Text("还没有下载任务").font(.title3.bold())
                        Text("在“解析”页粘贴分享链接，\n选择想保存的内容，就会出现在这里。")
                            .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 64)
                } else {
                    LazyVStack(spacing: 14) {
                        ForEach(downloads.records) { record in
                            DownloadRow(record: record,
                                        fileURL: downloads.fileURL(for: record),
                                        cancel: { downloads.cancel(record.id) },
                                        retry: { downloads.retry(record.id) },
                                        remove: { downloads.removeRecord(record.id) },
                                        open: { url, share in presentation = FilePresentation(url: url, share: share) })
                        }
                    }
                    Text("删除记录会取消未完成的任务，已保存的媒体文件会保留。你可以在“文件”App 中管理 Downloads 文件夹。")
                        .font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading)
                }
            }.padding(20)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("下载")
        .sheet(item: $presentation) { item in
            if item.share { ActivitySheet(url: item.url) }
            else { FilePreviewSheet(url: item.url) }
        }
    }
}

private struct DownloadRow: View {
    let record: DownloadRecord
    let fileURL: URL?
    let cancel: () -> Void
    let retry: () -> Void
    let remove: () -> Void
    let open: (URL, Bool) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 13) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: record.kind == .image ? "photo" : record.kind == .audio ? "waveform" : "film")
                    .font(.title3).foregroundStyle(Brand.purple)
                    .frame(width: 44, height: 48)
                    .background(Brand.purple.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 5) {
                    Text(record.title).font(.subheadline.bold()).lineLimit(2)
                    Text("\(record.platform.title) · \(record.assetLabel)")
                        .font(.caption).foregroundStyle(.secondary).lineLimit(2)
                }
                Spacer(minLength: 0)
                Menu {
                    Button("删除记录", role: .destructive, action: remove)
                } label: {
                    Image(systemName: "ellipsis").frame(width: 30, height: 30)
                }.accessibilityLabel("任务选项")
            }
            HStack {
                Label(record.status.title, systemImage: statusIcon)
                    .font(.caption.weight(.semibold)).foregroundStyle(statusColor)
                Spacer()
                if record.status == .downloading, let progress = record.progress {
                    Text(progress, format: .percent.precision(.fractionLength(0)))
                        .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                }
            }
            if record.status == .downloading {
                if let progress = record.progress { ProgressView(value: progress).tint(Brand.purple) }
                else { ProgressView().frame(maxWidth: .infinity, alignment: .leading) }
            } else if [.resolving, .merging].contains(record.status) {
                ProgressView().frame(maxWidth: .infinity, alignment: .leading)
            }
            if let detail = record.detail {
                Text(detail).font(.caption).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            HStack(spacing: 12) {
                if record.status.isPending {
                    Button("取消", action: cancel).buttonStyle(.bordered)
                } else if [.failed, .cancelled].contains(record.status) {
                    Button(action: retry) { Label("重新下载", systemImage: "arrow.clockwise") }
                        .buttonStyle(.bordered)
                } else if let fileURL {
                    Button { open(fileURL, false) } label: { Label("打开", systemImage: "doc") }
                        .buttonStyle(.bordered)
                    Button { open(fileURL, true) } label: { Label("分享", systemImage: "square.and.arrow.up") }
                        .buttonStyle(.borderedProminent)
                } else if record.status == .completed {
                    Text("文件已被移动或删除。").font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
            .font(.caption.weight(.semibold))
            .controlSize(.small)
        }
        .padding(16)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))
    }

    private var statusColor: Color {
        switch record.status {
        case .completed: return .green
        case .failed: return .orange
        case .cancelled: return .secondary
        default: return Brand.purple
        }
    }
    private var statusIcon: String {
        switch record.status {
        case .completed: return "checkmark.circle.fill"
        case .failed: return "exclamationmark.circle.fill"
        case .cancelled: return "xmark.circle"
        case .queued: return "clock"
        case .resolving: return "link"
        case .downloading: return "arrow.down.circle"
        case .merging: return "film.stack"
        }
    }
}

private struct FilePresentation: Identifiable {
    let id = UUID()
    let url: URL
    let share: Bool
}

private struct ActivitySheet: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

private struct FilePreviewSheet: View {
    let url: URL
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            QuickLookFile(url: url)
                .navigationTitle("文件预览")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }
    }
}

private struct QuickLookFile: UIViewControllerRepresentable {
    let url: URL
    func makeCoordinator() -> Coordinator { Coordinator(url: url) }
    func makeUIViewController(context: Context) -> QLPreviewController {
        let controller = QLPreviewController()
        controller.dataSource = context.coordinator
        return controller
    }
    func updateUIViewController(_ controller: QLPreviewController, context: Context) {}

    final class Coordinator: NSObject, QLPreviewControllerDataSource {
        let url: URL
        init(url: URL) { self.url = url }
        func numberOfPreviewItems(in controller: QLPreviewController) -> Int { 1 }
        func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem {
            url as NSURL
        }
    }
}
