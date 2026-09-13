# 媒体下载

媒体下载（FlowFrame）是一款面向 Android 的开源公开视频保存工具。把抖音分享文案、抖音链接、`b23.tv` 短链或哔哩哔哩视频链接粘贴进应用，即可预览内容并选择保存方式。

当前已发布 **1.2.2 图标与名称更新版（测试版）**：桌面名称直接说明用途，图标以圆角方形为设计基础，并适配系统的图标裁切与单色主题。功能沿用 1.2.1，版本说明与本轮验证状态见 [1.2.2 更新说明](docs/releases/1.2.2.md)。手机 APK 提供 `arm64-v8a`，支持目标为 Android 7.0 及以上。此前的结构优化和平台验收分别见 [1.2.1 优化报告](docs/OPTIMIZATION-1.2.1.md) 与 [1.2.0 测试报告](docs/VERIFICATION-1.2.0.md)；其余原生组件仍有 RELRO 对齐缺口，尚不能承诺全面兼容 16 KiB 设备。

[查看安装包](../../releases) · [查看更新记录](CHANGELOG.md) · [开发指南](docs/DEVELOPMENT.md) · [参与贡献](CONTRIBUTING.md)

## 功能

- 以“视频与图集下载”为主标题的原生界面，真实封面、图集分页浏览和图片勾选。
- 新增小红书、微博、快手的免登录公开页面适配；平台页面可能限制匿名请求，只有通过真实解析、下载验收的能力才列为已验证。
- 处理单视频与纯图集；混合正文图片和视频、多视频合集、直播、登录内容会明确提示不支持。
- 图集支持实际 JPEG / PNG / WebP 格式，按勾选后的原始顺序保存或合成视频。
- 可通过系统目录选择器指定保存位置，授权失效时要求重新选择；默认沿用系统媒体目录。
- 提供可复制的脱敏诊断、完整格式信息和开源说明入口；取消任务独立显示，迟到的回调不会将其重新变为下载中。

- 支持无需登录的抖音公开视频、抖音图文作品和哔哩哔哩公开视频。
- 自动从完整分享文案中提取并净化唯一链接；检测到多个链接时不会擅自选择。
- 抖音图文可选择保存选中的图片、原始 MP3，或合成为 1080×1920 H.264/AAC MP4。
- 根据实际可用格式提供推荐、最高画质、节省空间和仅音频方案；单一画质只显示一个保存选项。
- 竖屏画质按短边选择：推荐最高 1080 档，节省空间最高 720 档。
- 使用 FFmpeg 合并分离的音视频流，并在图文合成时完整保留原图画面。
- 支持后台队列、前台进度通知、网络恢复重试、取消、失败重试和 1–3 个并发任务。
- 任务记录持久化；完成后可以打开、分享或仅删除记录，删除记录不会移除系统媒体库中的文件。
- Android 10 及以上保存到系统 `影片/FlowFrame`、`音乐/FlowFrame` 或 `图片/FlowFrame`。
- 支持 Wi‑Fi 限制、深浅主题和系统动态配色。
- 输入限制为 16 KiB，并拒绝仿冒域名、URL 凭据和非标准端口。已知官方 HTTP 短链先提升为 HTTPS，再发送请求；不请求明文 HTTP 内容。

## 使用

1. 手机请下载 [FlowFrame-1.2.2-arm64-release.apk](https://github.com/mdmm90340-sketch/FlowFrame/releases/download/v1.2.2/FlowFrame-1.2.2-arm64-release.apk)。[发布页](https://github.com/mdmm90340-sketch/FlowFrame/releases/tag/v1.2.2)中的 `x86_64-test` 包仅用于对应架构的虚拟机；源码与开发验证归档不需要安装。
2. 在 Android 系统中允许本次来源安装应用，然后覆盖安装即可保留原任务记录。
3. 粘贴链接或完整分享文案，确认自动提取的链接后点击“解析”。
4. 选择画质或图文输出方式并加入下载。

每个 Release 都附有 SHA-256 校验文件。平台页面和解析规则可能变化，存在解析器并不意味着任意链接在任意时间都一定可用。

## 使用边界

FlowFrame 只处理公开内容，不读取账号 Cookie、不申请全盘文件权限，也不支持私密、付费或 DRM 内容。请仅保存你本人创作、已获授权或依法有权保存的内容，并遵守平台规则及所在地法律。平台名称和商标归各自权利人所有，本项目与抖音、哔哩哔哩及上游项目均无官方隶属关系。

## 从源码构建

需要 JDK 17、Android SDK 35 和 Build Tools 35.0.0。让 Android Studio 生成本机 `local.properties` 后运行：

```powershell
.\gradlew.bat testDebugUnitTest lintRelease assembleDebug
```

为 x86_64 模拟器生成测试包：

```powershell
.\gradlew.bat assembleDebug '-Pflowframe.testAbi=x86_64'
```

不传该参数时默认构建 `arm64-v8a`。源码构建不需要发布签名；签名配置、完整环境说明和发版清单分别见 [开发指南](docs/DEVELOPMENT.md) 与 [发布指南](docs/RELEASING.md)。

内置解析内核固定在官方 yt-dlp `2026.08.19`，不会在运行时静默更新。固定提交、三个文本补丁和可复现入口均在 [`tools/`](tools/)；架构与数据流见 [架构说明](docs/ARCHITECTURE.md)。

## 开源来源与共同创作

应用界面、任务模型、链接白名单、存储与后台调度由本项目实现；Android 解析运行环境来自 `youtubedl-android`，站点解析来自 yt-dlp，并对抖音公开内容兼容性应用了可审计的固定补丁。

FlowFrame 由项目所有者与 OpenAI ChatGPT / Codex 共同创作：所有者提出产品方向、交互需求并参与真机验收，ChatGPT / Codex 协助设计、实现、调试和整理文档。[OpenAI 的官方 GitHub 身份](https://github.com/openai)是组织而非可接受个人仓库邀请的个人账号，因此本项目在文档中明确署名，不向同名非官方账号授予权限。详见 [共同创作与致谢](ACKNOWLEDGEMENTS.md) 和 [第三方开源说明](THIRD_PARTY_NOTICES.md)。

## 许可证

本项目按 [GNU General Public License v3.0](LICENSE)（SPDX：`GPL-3.0-only`）发布。欢迎提交 Issue 和 Pull Request；贡献约定见 [CONTRIBUTING.md](CONTRIBUTING.md)。
