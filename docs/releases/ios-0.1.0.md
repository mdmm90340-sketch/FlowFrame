# 媒体下载 · iOS 0.1.0 实验版（未签名）

FlowFrame 首个独立的原生 iOS 客户端，最低 iOS 16。使用 SwiftUI、URLSession 和 AVFoundation，实现链接输入、资源预览与选择、前台下载队列、音视频合并、取消重试、任务记录以及系统预览和分享。

## 下载与安装

下载 `FlowFrame-iOS-0.1.0-unsigned.ipa`。这是 ARM64 设备构建，**未经过 Apple 签名，普通 iPhone 不能下载后直接安装**。需要自己的 Apple 签名和匹配的描述文件，或在 Mac 上打开源码工程、选择自己的 Team 后安装。安卓 `.jks` 密钥不能用于 iOS。

构建方式见 [iOS README](https://github.com/mdmm90340-sketch/FlowFrame/blob/main/ios/README.md)，完整验证边界见 [验证记录](https://github.com/mdmm90340-sketch/FlowFrame/blob/main/ios/VERIFICATION.md)。Release 附带 SHA-256、构建来源、模拟器首页截图、测试证据归档和验证记录。二进制不放入 Git 源码历史。

## 本次验证

[完整构建与测试已通过](https://github.com/mdmm90340-sketch/FlowFrame/actions/runs/36602064092)：14 项解析与链接测试、4 项模拟器媒体处理测试、4 项 UI 测试，以及 ARM64 iPhoneOS Release 编译。三张截图来自成功的 UI 测试。本地已再次核对 IPA ZIP 完整性、ARM64 架构、版本和未签名状态。

二进制源码提交：`14416d631dcd38d4440980fb2d436904e6cb2e7a`。版本标签在此之后仅增加验证与发布文档，具体文件见 `release-provenance.json`。IPA 大小为 395,802 字节，SHA-256：

```text
5e81d5851864f2d9834b356dae593a40d6d670acde12c7a9ca5a913f67c0fa52
```

`SHA256SUMS.txt` 为 CI 原始产物校验清单，`RELEASE-SHA256SUMS.txt` 覆盖额外发布附件；公开重新下载及源码克隆的核验结果见 `release-verification.json`。

## 功能与限制

- B 站原生解析路径处理匿名响应中的 AVC/AAC DASH 或单文件 MP4；分离音视频使用 AVFoundation 合并。
- 抖音视频、图集和原音解析仍属实验功能。合成 JSON 测试通过不代表线上可用；本次真实公开样例没有取得媒体 URL，未通过线上解析验收。
- B 站已完成 Windows 匿名 API 和少量媒体字节探测，iOS 端的真实平台完整下载、合并、导出尚未验证。
- 下载按前台运行设计，应用被系统挂起或终止后可能需要重新解析重试；首版不保证后台持续下载。
- 尚未移植小红书、微博、快手、图集合成 MP4、广泛转码、分段拼接及 Android 的 yt-dlp/FFmpeg 内核。
- 未进行真实 iPhone 签名安装、iPad 或最低 iOS 16 系统的实机测试；本版不等同于 Android 1.2.2 的功能覆盖。

仅保存自己创作、已获授权或依法有权保存的公开内容。源码按 GPL-3.0-only 发布，不包含 Apple 或 Android 的私钥、密码或描述文件。
