# 架构说明

FlowFrame 是单 Activity、Jetpack Compose、WorkManager 驱动的 Android 应用。代码保持单 `app` 模块，但按输入、引擎、任务、存储和界面分离职责。

## 主流程

```text
分享文案 / URL
      │
      ▼
SupportedUrlParser ── 唯一 HTTPS 白名单链接
      │
      ▼
DownloadEngine ── 固定 yt-dlp 内核解析
      │
      ▼
MediaPreview ── 用户选择画质或图文输出方式
      │
      ▼
DownloadRepository ── TaskStore + WorkManager
      │
      ▼
DownloadWorker ── 下载 / FFmpeg 合并 / 图文合成
      │
      ▼
MediaPublisher ── MediaStore 或 Android 7–9 应用目录
```

## 模块职责

- `SupportedUrlParser` 只接受 HTTPS、标准端口、无凭据且命中明确平台域名和路径的链接。分享文案被规范化为无链接、唯一链接或多个链接三种结果。
- `BundledYtDlpInstaller` 核对 APK 内内核版本与 SHA-256，再交给 `DownloadEngine`。应用不提供运行时在线更新或自定义命令入口。
- `DownloadEngine` 负责平台解析、公开格式选择、固定请求头和 yt-dlp 进程取消；短边分辨率规则在这里形成白名单参数。
- `DownloadRepository` 创建持久化 `DownloadTask`，并以任务 UUID 创建唯一 WorkManager 工作。
- `TaskStore` 使用 `AtomicFile` 保存 JSON，并保留旧 `outputLocation` 到多输出 `outputLocations` 的兼容读取。
- `DownloadWorker` 执行下载、刷新过期图文 CDN 地址、合并、进度通知、失败分类、回滚和暂存清理。
- `MediaPublisher` 在 Android 10+ 通过 `IS_PENDING` 实现媒体发布；多图全部写入成功后才统一公开，失败会删除本批次已创建项。
- Compose UI 只消费 `FlowFrameUiState`。任务菜单按阶段显示“取消任务”或“删除记录”；删除记录只修改 `TaskStore`，不删除已经发布的媒体文件。

## 视频与图文

普通视频由 yt-dlp 选择公开格式并调用 FFmpeg 合并。图文解析结果通过固定的 `flowframe_gallery` v1 元数据进入应用：

- 图片模式：逐张下载并验证，再按 `001…` 顺序批量发布 WebP。
- 音频模式：保存作品提供的原始 MP3；无音频时该选项不可用。
- MP4 模式：下载所有图片和可选音频，通过 FFmpeg 生成 1080×1920、H.264/AAC、30fps 视频；无音频时每张显示 3 秒。

所有 FFmpeg 参数由代码构造为固定参数列表，不经过 shell。取消任务时终止进程并清理暂存内容。

## 持久化与兼容

`DownloadTask` 是任务历史的序列化边界。新增字段必须有默认值，未知字段由 JSON 解码器忽略。修改模型时必须保留 `DownloadTaskMigrationTest`，并覆盖旧任务 JSON。

设置使用独立的 `AppSettingsStore`。短期 CDN 和缩略图 URL 不作为稳定任务数据保存；需要时重新解析公开作品。

## 测试边界

JVM 测试覆盖链接净化与恶意 URL、失败分类与日志脱敏、任务迁移、图文时间线和 UI 阶段动作。涉及平台网络、FFmpeg、MediaStore、取消和厂商系统行为的改动仍需在模拟器或实体设备做端到端回归。
