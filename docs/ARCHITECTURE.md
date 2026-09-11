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
DownloadEngine ── 公开页面适配 + 固定 yt-dlp 内核解析
      │
      ▼
MediaPreview ── 用户选择画质、图片子集和输出方式
      │
      ▼
DownloadRepository ── TaskStore + WorkManager
      │
      ▼
DownloadWorker ── 下载 / FFmpeg 合并 / 图文合成
      │
      ▼
MediaPublisher ── SAF 所选目录 / MediaStore / Android 7–9 应用目录
```

## 模块职责

- `SupportedUrlParser` 校验标准端口、无凭据、明确域名和作品路径；已知官方 HTTP 短链先升级 HTTPS，实际请求只用 HTTPS。分享文案被规范化为无链接、唯一链接或多个链接三种结果。
- `BundledYtDlpInstaller` 核对 APK 内内核版本与 SHA-256，再交给 `DownloadEngine`。应用不提供运行时在线更新或自定义命令入口。
- `BundledNativeInstaller` 在同一初始化锁内核对当前 ABI 的五个 WebP 库哈希；旧缓存不匹配时仅重新提取私有 FFmpeg 包，初始化后再次校验。重编来源与 16 KiB 验证见 `WEBP-16K.md`。
- `DownloadEngine` 负责平台解析、公开格式选择、固定请求头和 yt-dlp 进程取消；短边分辨率规则在这里形成白名单参数。
- `core/platform` 将公开页面解析分为请求、纯数据解析和能力目录；拒绝登录受限、混合、多视频和不完整图集。微博/小红书单视频保留原作品 URL 交给内核选格式。平台与作品 ID 共同标识预览。
- `DownloadRepository` 创建持久化 `DownloadTask`，并以任务 UUID 创建唯一 WorkManager 工作。
- `TaskStore` 在 IO 调度器用 `AtomicFile` 保存 JSON，等待加载完成再修改；终态不可被旧进度覆盖，普通进度按约 1.5 秒窗口合并，终态立即保存。有待保存变化时才安排一个延迟写入；失败保留最新快照并延迟重试，空闲时不轮询。同值更新和不存在记录的删除不写入、不重建记录。损坏文件保留且禁止静默覆盖，旧单输出记录兼容读取。
- `DownloadWorker` 执行下载、刷新过期图文 CDN 地址、合并、进度通知、失败分类、回滚和暂存清理。
- `MediaPublisher` 在 Android 10+ 通过 `IS_PENDING` 实现媒体发布；多图全部写入成功后才统一公开，失败会删除本批次已创建项。
- 自选目录通过 SAF 持久授权写入，不推测文件路径；失效授权显式报错。发布失败/取消只回滚本批新文件。任务完成记录与输出保留状态同步提交，避免完成后被取消清理。
- `NetworkMonitor` 监听已验证网络和实际 Wi-Fi 传输类型；Worker 先等待允许网络再申请并发，网络丢失时中止当前传输并等待自动重试。
- `DownloadConcurrencyGate` 监听使用中的名额和原始设置流，名额释放或设置变化时才唤醒等待者；锁内重新读取当前上限，避免派生状态异步滞后导致超额启动。降低上限只约束之后的启动，不中断已有下载。
- Compose UI 只消费 `FlowFrameUiState`。任务菜单按阶段显示“取消任务”或“删除记录”；删除记录只修改 `TaskStore`，不删除已经发布的媒体文件。
- `TaskUiProjector` 集中任务记录到界面行的转换，输入显式包含网络约束和内存缩略图快照；未变化行沿用原对象，删除记录时清理对应缓存。主题或目录变化不再重复转换任务历史。
- `EditorRecoverySnapshot` 只提取输入、页面和预览选择等恢复字段；`MainViewModel` 去重后才写入 `SavedStateHandle`，任务进度不会触发草稿重复保存。

## 视频与图文

普通视频由 yt-dlp 选择公开格式并调用 FFmpeg 合并。图文解析结果通过固定的 `flowframe_gallery` v1 元数据进入应用：

- 图片模式：按原始索引排序所选图片，逐张下载并验证真实 JPEG、PNG、WebP 字节，按 `001…` 顺序批量发布。
- 音频模式：保存作品提供的原始音频；无音频时该选项不可用。
- MP4 模式：下载所选图片和可选音频，通过 FFmpeg 生成 1080×1920、H.264/AAC、30fps 视频；无音频时每张显示 3 秒。准备画布前对大图采样，进度读取 FFmpeg 实际输出时间。

所有 FFmpeg 参数由代码构造为固定参数列表，不经过 shell。取消任务时终止进程并清理暂存内容。

## 持久化与兼容

`DownloadTask` 是任务历史的序列化边界。新增字段必须有默认值，未知字段由 JSON 解码器忽略。修改模型时必须保留 `DownloadTaskMigrationTest`，并覆盖旧任务 JSON。

设置使用独立的 `AppSettingsStore`，包含持久目录授权 URI。任务保存图片子集和创建时目标目录快照；重试重新入队并使用当前目录。短期 CDN 和缩略图 URL 不作为稳定任务数据保存；需要时重新解析公开作品。输入、页面和预览选择用 `SavedStateHandle` 恢复，进程重建后重新解析预览。

## 测试边界

JVM 测试覆盖链接净化与恶意 URL、公开数据适配、失败分类与日志脱敏、任务迁移/终态、真实传输进度、网络策略、图文时间线和 UI 阶段动作。设备测试覆盖真实 TaskStore 持久化、Compose 操作及原生 FFmpeg 合成/完整解码/媒体库回读。平台网络与厂商行为仍按本地测试报告明确区分已验证、阻断和未验证。
