# 参与贡献

感谢你愿意改进 FlowFrame。提交代码前，请先确认改动仍遵守项目只处理无需登录的公开内容这一边界。

## 开始之前

1. 阅读 [开发指南](docs/DEVELOPMENT.md)、[架构说明](docs/ARCHITECTURE.md) 和 [第三方开源说明](THIRD_PARTY_NOTICES.md)。
2. 从 `main` 创建短期分支；一个 Pull Request 尽量只解决一个问题。
3. Bug 修复应附带能在修复前失败、修复后通过的测试或可重复验证步骤。
4. 不要提交真实 Cookie、账号信息、私密链接、下载媒体、设备序列号、日志原文、签名文件或本机路径。

## 本地验证

至少运行：

```powershell
.\gradlew.bat testDebugUnitTest lintRelease assembleDebug
```

涉及解析、下载、MediaStore、FFmpeg 或任务取消时，还应在对应 ABI 的模拟器或实体设备完成端到端验证。平台链接可以在 Issue 中提供经过脱敏且仍可复现的公开样例，但不要提交你无权公开的内容。

## 解析内核与第三方代码

- 不接受应用运行时静默下载或执行新版解析内核。
- 修改 `tools/patches/` 时必须记录来源提交、许可证、补丁 SHA-256，并同步更新内核 manifest、NOTICE 和可复现构建结果。
- 不应把上游作者添加为仓库协作者来代替致谢；协作者权限只授予实际参与仓库维护的人。
- 提交者必须有权按 GPL-3.0 提供自己的改动，并保留引用代码的原许可证和版权声明。

## AI 辅助贡献

允许使用 ChatGPT、Codex 或其他工具辅助开发。提交者仍需理解、测试并对提交内容负责；如果 AI 对设计或实现有实质贡献，建议在 Pull Request 说明中披露。不要伪造不存在的 GitHub 账号或 `Co-authored-by` 邮箱。

## Pull Request 清单

- [ ] 功能边界、权限和联网范围没有被无意扩大。
- [ ] 新行为已有测试或明确的设备回归证据。
- [ ] JVM 测试、Release Lint 和 Debug 构建通过。
- [ ] 用户可见变化已写入 `CHANGELOG.md`。
- [ ] 文档、版本号、NOTICE 与第三方归属已经同步。
