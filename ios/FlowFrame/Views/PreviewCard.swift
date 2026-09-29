import SwiftUI
import FlowFrameCore

struct PreviewCard: View {
    let preview: MediaPreview
    let onDownload: ([MediaAsset]) -> Void
    @State private var selected: Set<String>

    init(preview: MediaPreview, onDownload: @escaping ([MediaAsset]) -> Void) {
        self.preview = preview
        self.onDownload = onDownload
        if let video = preview.assets.first(where: { $0.kind == .video }) {
            _selected = State(initialValue: [video.id])
        } else if preview.assets.contains(where: { $0.kind == .image }) {
            _selected = State(initialValue: Set(preview.assets.filter { $0.kind == .image }.map(\.id)))
        } else {
            _selected = State(initialValue: Set(preview.assets.prefix(1).map(\.id)))
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top, spacing: 14) {
                SecureThumbnail(url: preview.thumbnailURL)
                    .frame(width: 88, height: 112)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                VStack(alignment: .leading, spacing: 8) {
                    Text(preview.platform.title).font(.caption.bold()).foregroundStyle(Brand.purple)
                    Text(preview.title).font(.headline).lineLimit(4)
                    if let uploader = preview.uploader, !uploader.isEmpty {
                        Label(uploader, systemImage: "person.crop.circle").font(.caption).foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 0)
            }
            Divider()

            if !videos.isEmpty {
                Text("视频画质 · 单选").font(.subheadline.bold())
                ForEach(videos) { asset in
                    option(asset, systemImage: selected.contains(asset.id) ? "largecircle.fill.circle" : "circle")
                }
                if videos.contains(where: { $0.companionAudioURL != nil }) {
                    Text("分离的音视频轨道会在本机合并为 MP4。")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            if !audios.isEmpty {
                Text("独立音频 · 可选").font(.subheadline.bold())
                ForEach(audios) { asset in
                    option(asset, systemImage: selected.contains(asset.id) ? "checkmark.circle.fill" : "circle")
                }
            }
            if !images.isEmpty {
                HStack {
                    Text("图片 · \(images.count) 张").font(.subheadline.bold())
                    Spacer()
                    Button(images.allSatisfy { selected.contains($0.id) } ? "取消全选" : "全选") {
                        if images.allSatisfy({ selected.contains($0.id) }) {
                            selected.subtract(images.map(\.id))
                        } else { selected.formUnion(images.map(\.id)) }
                    }.font(.caption.bold())
                }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 88), spacing: 10)], spacing: 10) {
                    ForEach(images) { asset in
                        Button { toggle(asset) } label: {
                            ZStack(alignment: .topTrailing) {
                                SecureThumbnail(url: asset.url, headers: asset.headers)
                                    .frame(height: 105).clipped()
                                Image(systemName: selected.contains(asset.id) ? "checkmark.circle.fill" : "circle")
                                    .symbolRenderingMode(.palette)
                                    .foregroundStyle(selected.contains(asset.id) ? Brand.purple : .gray, .white)
                                    .font(.title3).padding(6)
                            }
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(asset.label)
                        .accessibilityAddTraits(selected.contains(asset.id) ? .isSelected : [])
                    }
                }
            }

            Button { onDownload(preview.assets.filter { selected.contains($0.id) }) } label: {
                Label("下载所选内容（\(selected.count)）", systemImage: "arrow.down.to.line")
                    .fontWeight(.semibold).frame(maxWidth: .infinity).padding(.vertical, 7)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.roundedRectangle(radius: 14))
            .disabled(selected.isEmpty)
            Text("下载时请保持 App 在前台，切到后台可能中断。")
                .font(.caption).foregroundStyle(.secondary)
        }
        .padding(18)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 24))
    }

    private var videos: [MediaAsset] { preview.assets.filter { $0.kind == .video } }
    private var images: [MediaAsset] { preview.assets.filter { $0.kind == .image } }
    private var audios: [MediaAsset] { preview.assets.filter { $0.kind == .audio } }

    private func option(_ asset: MediaAsset, systemImage: String) -> some View {
        Button { toggle(asset) } label: {
            HStack(spacing: 12) {
                Image(systemName: systemImage).font(.title3).foregroundStyle(Brand.purple)
                Text(asset.label).font(.subheadline).foregroundStyle(.primary)
                Spacer()
                Image(systemName: asset.kind == .audio ? "waveform" : "film").foregroundStyle(.secondary)
            }
            .padding(13)
            .background(selected.contains(asset.id) ? Brand.purple.opacity(0.08) : Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected.contains(asset.id) ? .isSelected : [])
    }

    private func toggle(_ asset: MediaAsset) {
        if selected.contains(asset.id) { selected.remove(asset.id) }
        else {
            if asset.kind == .video { selected.subtract(videos.map(\.id)) }
            selected.insert(asset.id)
        }
    }
}

struct SecureThumbnail: View {
    let url: URL?
    var headers: [String: String] = [:]
    @State private var image: UIImage?

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Brand.gradient.opacity(0.1)
                if let image {
                    Image(uiImage: image).resizable().scaledToFill()
                        .frame(width: geometry.size.width, height: geometry.size.height).clipped()
                } else {
                    Image(systemName: "photo").font(.title2).foregroundStyle(Brand.purple.opacity(0.5))
                }
            }
        }
        .accessibilityHidden(true)
        .task(id: url) {
            image = nil
            guard let url else { return }
            let local = FileManager.default.temporaryDirectory.appendingPathComponent("flowframe-thumb-\(UUID().uuidString)")
            defer { try? FileManager.default.removeItem(at: local) }
            do {
                let transfer = MediaTransfer(destination: local, kind: .image, sizeLimit: 16 * 1024 * 1024)
                _ = try await transfer.fetch(url, headers: headers)
                try Task.checkCancellation()
                image = try ImageFile.thumbnail(at: local, maximumPixelSize: 480)
            } catch { /* A missing thumbnail never prevents selecting or downloading. */ }
        }
    }
}
