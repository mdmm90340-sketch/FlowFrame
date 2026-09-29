import SwiftUI
import FlowFrameCore

struct HomeView: View {
    @EnvironmentObject private var downloads: DownloadStore
    let onEnqueued: () -> Void
    @State private var input = ""
    @State private var preview: MediaPreview?
    @State private var resolvedSource = ""
    @State private var parsing = false
    @State private var parsingTask: Task<Void, Never>?
    @State private var errorMessage: String?
    @FocusState private var inputFocused: Bool
    private let resolver = MediaResolver()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                HStack(spacing: 14) {
                    Image(systemName: "arrow.down.forward.circle.fill")
                        .font(.system(size: 42, weight: .medium))
                        .foregroundStyle(Brand.gradient)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 5) {
                        Text("FlowFrame").font(.title2.bold())
                        Text("链接里的精彩，随时留存。")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.top, 6)

                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Label("分享链接", systemImage: "link").font(.headline)
                        Spacer()
                        PasteButton(payloadType: String.self) { values in
                            input = values.joined(separator: "\n")
                            preview = nil
                        }
                        .buttonBorderShape(.capsule)
                        .controlSize(.small)
                        .disabled(parsing)
                    }
                    ZStack(alignment: .topLeading) {
                        if input.isEmpty {
                            Text("粘贴抖音或哔哩哔哩的分享文本…")
                                .font(.body).foregroundStyle(.tertiary)
                                .padding(.horizontal, 5).padding(.top, 8)
                                .allowsHitTesting(false)
                        }
                        TextEditor(text: $input)
                            .scrollContentBackground(.hidden)
                            .frame(minHeight: 105, maxHeight: 145)
                            .focused($inputFocused)
                            .disabled(parsing)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                            .accessibilityLabel("分享链接")
                            .accessibilityIdentifier("shareInput")
                    }
                    .padding(10)
                    .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))

                    Button(action: parse) {
                        HStack(spacing: 9) {
                            if parsing { ProgressView().tint(.white) }
                            else { Image(systemName: "wand.and.stars") }
                            Text(parsing ? "正在解析链接…" : "解析内容").fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 8)
                    }
                    .buttonStyle(.borderedProminent)
                    .buttonBorderShape(.roundedRectangle(radius: 14))
                    .disabled(parsing || input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .accessibilityIdentifier("parseButton")

                    if parsing {
                        Button("取消解析") {
                            parsingTask?.cancel()
                            parsingTask = nil
                            parsing = false
                        }
                        .font(.footnote)
                        .frame(maxWidth: .infinity)
                    } else {
                        HStack(spacing: 8) {
                            platformBadge("抖音", icon: "music.note")
                            platformBadge("哔哩哔哩", icon: "play.rectangle")
                            Spacer()
                            Text("公开内容").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                .padding(18)
                .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 24))

                if let preview {
                    PreviewCard(preview: preview) { assets in
                        downloads.enqueue(preview: preview, assets: assets, sourceText: resolvedSource)
                        onEnqueued()
                    }
                    .id(preview.id)
                } else {
                    VStack(alignment: .leading, spacing: 13) {
                        Text("三步，保存一份喜欢").font(.headline)
                        step("1", "复制分享链接", "在抖音或哔哩哔哩中复制公开作品链接。")
                        step("2", "预览并选择", "选择视频画质、音频或需要的图片。")
                        step("3", "下载到本机", "保持 App 在前台，完成后打开或分享。")
                    }.padding(.horizontal, 5)
                }

                NoticeCard(icon: "info.circle", text: "iOS 实验版 · 仅支持部分抖音、哔哩哔哩公开内容。登录、会员、私密及受保护内容不受支持。请仅保存你有权下载的内容。")
            }
            .padding(20)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("解析")
        .navigationBarTitleDisplayMode(.inline)
        .scrollDismissesKeyboard(.interactively)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("完成") { inputFocused = false }
            }
        }
        .alert("暂时无法解析", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("好", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "请稍后重试。") }
        .onDisappear {
            parsingTask?.cancel()
            parsingTask = nil
            parsing = false
        }
        .onChange(of: input) { _ in preview = nil }
    }

    private func parse() {
        inputFocused = false
        let source = input.trimmingCharacters(in: .whitespacesAndNewlines)
        do { _ = try ShareLinkParser.parse(source) }
        catch {
            errorMessage = (error as? FlowFrameError)?.localizedDescription ?? "请粘贴有效的抖音或哔哩哔哩分享链接。"
            return
        }
        parsing = true
        preview = nil
        parsingTask = Task {
            do {
                let result = try await resolver.resolve(source)
                try Task.checkCancellation()
                preview = result
                resolvedSource = source
                parsing = false
                parsingTask = nil
            } catch {
                guard !Task.isCancelled else { return }
                errorMessage = (error as? FlowFrameError)?.localizedDescription ?? "网络连接失败或平台暂时无法访问，请稍后重试。"
                parsing = false
                parsingTask = nil
            }
        }
    }

    private func platformBadge(_ title: String, icon: String) -> some View {
        Label(title, systemImage: icon).font(.caption.weight(.medium))
            .padding(.horizontal, 9).padding(.vertical, 5)
            .background(Brand.purple.opacity(0.07), in: Capsule())
    }

    private func step(_ number: String, _ title: String, _ detail: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text(number).font(.caption.bold()).foregroundStyle(Brand.purple)
                .frame(width: 26, height: 26).background(Brand.purple.opacity(0.1), in: Circle())
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.subheadline.weight(.medium))
                Text(detail).font(.footnote).foregroundStyle(.secondary)
            }
        }
    }
}
