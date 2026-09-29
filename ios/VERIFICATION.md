# iOS 0.1.0 验证记录

本文件区分源码测试、模拟器、设备编译和真实使用，不将 Android 的历史验收结果移用于 iOS。

当前处于首次构建验证阶段。最终结果随通过的 GitHub Actions run、`build-provenance.json`、测试结果和 Release 一起交付。

| 范围 | 状态 |
| --- | --- |
| Swift 解析器与链接单元测试 | 待 macOS CI 执行 |
| iPhone 模拟器 UI 测试 | 待 macOS CI 执行 |
| ARM64 iPhoneOS Release 编译 | 待 macOS CI 执行 |
| IPA 完整性与公开下载哈希 | 待发布核验 |
| 真实 iPhone 签名安装 | 未验证：未提供 Apple 签名 |
| 真实平台在线解析与下载 | 未验证 |
| 后台持续下载 | 首版不承诺，需保持前台 |

解析器测试数据为合成夹具，不含真实用户数据或登录凭据。

## 公开平台接口探测（Windows 主机，不是 iOS 端到端验收）

检查时间：2026-09-30 00:07–00:08（Asia/Shanghai）。使用本机 Python 标准库发送匿名 HTTPS 请求，不导入登录 Cookie；结果只说明该时间、该网络、该作品的接口状态。未保存或提交响应页面、带时效签名的 CDN URL 或完整媒体。

| 平台与公开样例 | 接口/字节证据 | iOS 实际调用与完整下载 |
| --- | --- | --- |
| [哔哩哔哩 BV17fTF6EEAc](https://www.bilibili.com/video/BV17fTF6EEAc) | `view` 与 `playurl` 均 HTTP 200、`code=0`，返回 BV 编号匹配的作品信息，以及 AVC 视频和 AAC 音轨。各选择一条白名单 `bilivideo.com` / `bilivideo.cn` 流，使用 `Range: bytes=0-63` 分别取得 HTTP 206、64 字节，两个流的 ISO BMFF 文件头均为 `ftyp`。 | 未验证。尚不能据此宣称 iOS 解析、完整传输、AVFoundation 合并或文件导出成功。 |
| [抖音 6961737553342991651](https://www.douyin.com/video/6961737553342991651) | `iesdouyin.com` 分享页 HTTP 200、32,566 字节，有 `_ROUTER_DATA` 外壳但没有目标作品媒体对象；无签名的公开 detail 请求 HTTP 200、响应体为空。没有取得可用媒体 URL，未请求媒体字节。 | 未通过在线解析验证；实现只能处理公开响应确实包含目标作品 JSON 的情况。平台要求验证、匿名接口返回空内容时会报错。 |

本次 iOS 适配没有移植 Android 内核中的 `a_bogus` 和匿名 `ttwid` Cookie 初始化。抖音的合成 JSON 夹具覆盖作品编号匹配、视频、图片顺序、原始音频、空响应和受限内容，不能证明线上解析成功。B 站仅选择免登录响应中可用的 AVC/AAC 或单文件 MP4；未知 CDN 主机、DRM、受限内容、FLV 和分段输出均不作为可下载结果。
