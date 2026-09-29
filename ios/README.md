# 媒体下载 · iOS 0.1.0 实验版

FlowFrame 的独立原生 iOS 客户端，使用 SwiftUI、URLSession 和 AVFoundation，最低 iOS 16。它与 Android 1.2.2 共用项目名称、用途和图标设计，但具有独立版本号、平台实现和验证范围。

**`FlowFrame-iOS-0.1.0-unsigned.ipa` 是未签名的 ARM64 设备构建，普通 iPhone 不能下载后直接安装。** 必须使用自己的 Apple 签名及匹配的描述文件，或在 Mac 上用 Xcode 构建到设备。安卓的 `.jks` 密钥不能用于 iOS。本仓库没有 Apple 私钥，也未上传任何签名材料。

## 首版范围

- 粘贴抖音、B 站的作品链接或分享文案，提取唯一支持的链接；输入大小、域名、端口和重定向均受校验。
- 通过原生解析器读取公开作品数据，展示标题、作者、封面和实际可用资源。
- 选择视频/音频或抖音图集图片，加入下载队列；必要时用 AVFoundation 合并 B 站 AVC 视频与 AAC 音轨。
- 显示实际传输进度；取消、重试、持久化任务记录、系统预览与文件分享。
- 文件存储在应用的 Documents 目录，可从系统“文件”访问。删除任务记录不删除已保存媒体。
- iPhone/iPad 原生布局，系统深浅外观。

首版按**前台下载**设计，请下载时保持应用打开。系统挂起、终止应用或网络中断后，任务可能需要重新解析和重试；不承诺后台持续下载。平台返回登录、验证或不可用内容时会明确报错，不导入账号 Cookie，不解锁受限内容。

小红书、微博、快手、图集合成 MP4、任意格式转码、安卓的 yt-dlp/FFmpeg 能力、播放列表与多段视频拼接尚未移植。接口会随平台变化；解析器存在和测试夹具通过不等于真实网络下载已通过。

## 构建

使用 Mac、Xcode 16.4 和 XcodeGen 2.44.1。Windows 可编辑源码，iOS 编译和模拟器测试由 GitHub Actions 的 macOS 环境执行。

```sh
swift test --package-path ios/FlowFrameCore
xcodegen generate --spec ios/project.yml
open ios/FlowFrame.xcodeproj
```

在 Xcode 中选择 `FlowFrame` scheme。模拟器可直接运行；真机需要在 Signing & Capabilities 选择自己的 Team，并为应用配置可用的 bundle identifier。仓库默认 ID 为 `com.flowframe.ios`。

命令行构建设备二进制（未签名）：

```sh
xcodebuild build -project ios/FlowFrame.xcodeproj -scheme FlowFrame \
  -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' \
  -derivedDataPath build/ios-device CODE_SIGNING_ALLOWED=NO
```

完整的生成、测试、打包和 SHA-256 流程在 [iOS CI](../.github/workflows/ios.yml)。XcodeGen 下载校验值固定，编译器版本和源码提交记录随产物上传。

## 验证与交付

每次成功的 iOS CI 包含：

1. Swift Package 单元测试：链接、URL 策略和平台解析数据。
2. iPhone 模拟器 UI 测试：启动、无效链接错误、页面导航与截图。
3. `iphoneos` Release 构建，检查 ARM64 架构和未签名状态。
4. IPA ZIP 完整性和 SHA-256、源码提交及测试结果元数据。

上述测试不覆盖 iPhone 签名安装、真实站点解析下载成功率或长期后台运行。首版在线验收与真实设备验收状态见 [验证记录](VERIFICATION.md)，不得以“编译通过”代替。

## 源码结构

- `FlowFrameCore/`：无第三方依赖的 Swift Package，包含链接策略、媒体模型及平台解析器。
- `FlowFrame/`：SwiftUI 界面、下载队列、任务记录、AVFoundation 合并和系统分享。
- `FlowFrameUITests/`：设备模拟器 UI 测试。
- `project.yml` / `Info.plist`：可审阅的 XcodeGen 工程定义和应用属性。

本实现未嵌入或执行安卓的 Python/FFmpeg 二进制。iOS 部分同样遵守仓库 GPL-3.0-only 许可证。仅保存自己创作、已获授权或依法有权保存的公开内容。

参考：[Apple 构建设置](https://developer.apple.com/documentation/xcode/configuring-the-build-settings-of-a-target/)、[Apple 真机签名](https://help.apple.com/xcode/mac/current/en.lproj/dev5a825a1ca.html)、[XcodeGen 工程规范](https://github.com/yonaskolb/XcodeGen/blob/master/Docs/ProjectSpec.md)。
