import SwiftUI

struct AboutView: View {
    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 12) {
                    Image(systemName: "arrow.down.forward.circle.fill")
                        .font(.system(size: 46)).foregroundStyle(Brand.gradient).accessibilityHidden(true)
                    Text("FlowFrame").font(.title.bold())
                    Text("iOS 实验版").font(.subheadline.weight(.semibold)).foregroundStyle(Brand.purple)
                    Text("版本 \(version) · 构建 \(build)").font(.caption).foregroundStyle(.secondary)
                    Text("从分享链接开始，在自己的设备上整理喜欢的内容。")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                .padding(.vertical, 10)
            }
            Section("首版支持") {
                info("link", "分享链接解析", "支持部分抖音短视频、图集，以及哔哩哔哩公开视频链接。")
                info("film", "选择后保存", "视频画质单选、图片多选、独立音频。分离音视频使用 iOS 原生能力合并为 MP4。")
                info("square.and.arrow.up", "文件与分享", "保存到本机 Downloads 文件夹，支持系统预览、分享与“文件”App 管理。")
            }
            Section("使用限制") {
                info("iphone", "下载时保持前台", "不提供持续后台下载。切到后台或退出 App 可能中断；重试会重新解析链接并从头下载，不支持断点续传。")
                info("person.crop.circle.badge.exclamationmark", "不支持登录内容", "没有账号登录或 Cookie 导入。不支持会员、私密、付费、受 DRM 保护内容，也不绕过平台访问限制。")
                info("rectangle.stack", "仍在适配中", "仅保存当前解析结果，不提供合集批量抓取、平台搜索、字幕或直播下载。功能与 Android 版不完全相同。")
                info("network", "依赖平台可用性", "平台规则、区域、网络与链接有效期都可能影响解析和下载。部分编码无法在 iOS 上合并。")
            }
            Section("本机数据与隐私") {
                info("folder", "你来管理文件", "任务和原始分享文本保存在本机，媒体保存在“我的 iPhone / FlowFrame / Downloads”。删除任务记录会保留已完成文件；卸载 App 会移除其本机数据。")
                info("hand.raised", "按需访问网络", "解析与下载会向对应平台及其媒体 CDN 请求内容。没有自建上传服务、广告或分析 SDK。剪贴板只通过你点击系统粘贴按钮读取。")
                Text("请遵守平台规则与创作者权利，仅下载你有权保存和使用的内容。")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle("关于 FlowFrame")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var version: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.1.0" }
    private var build: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1" }
    private func info(_ icon: String, _ title: String, _ detail: String) -> some View {
        Label {
            VStack(alignment: .leading, spacing: 5) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(detail).font(.footnote).foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }.padding(.vertical, 4)
        } icon: { Image(systemName: icon).foregroundStyle(Brand.purple) }
    }
}
