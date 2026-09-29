# iOS 0.1.0 验证记录

本文件区分源码测试、模拟器、设备编译和真实使用，不将 Android 的历史验收结果移用于 iOS。

验证日期：2026-09-30（Asia/Shanghai）。[GitHub Actions 36602064092](https://github.com/mdmm90340-sketch/FlowFrame/actions/runs/36602064092) 完整通过。二进制构建提交为 `14416d631dcd38d4440980fb2d436904e6cb2e7a`，工具链为 Xcode 16.4 / Swift 6.1.2。构建后仅补充验证与发布文档，差异文件记录在 Release 的 `release-provenance.json` 中。

| 范围 | 状态 |
| --- | --- |
| Swift 解析器与链接单元测试 | 14 项通过，0 失败 |
| 模拟器媒体处理测试 | 4 项通过；合成 H.264/AAC 实际合并、取消、伪媒体与无效轨道拒绝 |
| iPhone 模拟器 UI 测试 | iPhone 16 Pro / iOS 18.5，4 项通过；首页输入、错误提示、页面导航及截图 |
| ARM64 iPhoneOS Release 编译 | 通过，未签名；本地再核对 Mach-O 架构、包标识、版本和最低系统版本 |
| IPA ZIP 完整性与 SHA-256 | CI 及 Windows 独立校验通过；公开下载核验记录随 Release 的 `release-verification.json` 提供 |
| 真实 iPhone 签名安装 | 未验证：未提供 Apple 签名 |
| 真实平台在线解析与下载 | 未验证 |
| iPad 与最低 iOS 16 实测 | 未验证；部署目标不等于已在全部设备上验收 |
| 后台持续下载 | 首版不承诺，需保持前台 |

IPA 为 395,802 字节，SHA-256：`5e81d5851864f2d9834b356dae593a40d6d670acde12c7a9ca5a913f67c0fa52`。Release 附带 CI 原始校验清单、构建来源、三张模拟器截图、完整测试结果及日志。解析器和媒体测试使用合成夹具，不含真实用户数据或登录凭据。

同一构建提交的前一次运行 `36600674124` 曾在 XCTest 获取应用后台断言时失败；同轮执行相同导航辅助函数的截图测试通过，随后同一提交、相同断言的完整重跑通过。本记录不把该系统错误归因于已证实的根因，也没有通过跳过测试或降低断言取得通过结果。截图从成功测试的 `.xcresult` 附件直接导出。

## 公开平台接口探测（Windows 主机，不是 iOS 端到端验收）

检查时间：2026-09-30 00:07–00:08（Asia/Shanghai）。使用本机 Python 标准库发送匿名 HTTPS 请求，不导入登录 Cookie；结果只说明该时间、该网络、该作品的接口状态。未保存或提交响应页面、带时效签名的 CDN URL 或完整媒体。

| 平台与公开样例 | 接口/字节证据 | iOS 实际调用与完整下载 |
| --- | --- | --- |
| [哔哩哔哩 BV17fTF6EEAc](https://www.bilibili.com/video/BV17fTF6EEAc) | `view` 与 `playurl` 均 HTTP 200、`code=0`，返回 BV 编号匹配的作品信息，以及 AVC 视频和 AAC 音轨。各选择一条白名单 `bilivideo.com` / `bilivideo.cn` 流，使用 `Range: bytes=0-63` 分别取得 HTTP 206、64 字节，两个流的 ISO BMFF 文件头均为 `ftyp`。 | 未验证。尚不能据此宣称 iOS 解析、完整传输、AVFoundation 合并或文件导出成功。 |
| [抖音 6961737553342991651](https://www.douyin.com/video/6961737553342991651) | `iesdouyin.com` 分享页 HTTP 200、32,566 字节，有 `_ROUTER_DATA` 外壳但没有目标作品媒体对象；无签名的公开 detail 请求 HTTP 200、响应体为空。没有取得可用媒体 URL，未请求媒体字节。 | 未通过在线解析验证；实现只能处理公开响应确实包含目标作品 JSON 的情况。平台要求验证、匿名接口返回空内容时会报错。 |

本次 iOS 适配没有移植 Android 内核中的 `a_bogus` 和匿名 `ttwid` Cookie 初始化。抖音的合成 JSON 夹具覆盖作品编号匹配、视频、图片顺序、原始音频、空响应和受限内容，不能证明线上解析成功。B 站仅选择免登录响应中可用的 AVC/AAC 或单文件 MP4；未知 CDN 主机、DRM、受限内容、FLV 和分段输出均不作为可下载结果。
