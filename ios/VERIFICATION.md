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
