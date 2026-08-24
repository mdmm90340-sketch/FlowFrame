# 安全设计与报告

## 已实施边界

- 分享文本只接受唯一合法 HTTPS URL；多个受支持链接不会猜测，并限制为抖音、哔哩哔哩已知域名。
- 拒绝非 HTTPS、非标准端口、带用户名密码、超长、换行或伪装后缀域名的地址。
- 不把分享文本拆成命令参数；yt-dlp 参数只由应用内部白名单生成。
- 不提供自定义命令、`--exec`、自动解析内核更新或 Cookie 导入。
- 解析内核固定为可复现构建产物；启动时同时核对版本 marker 和 SHA-256。升级安装若遗留旧内核，只删除私有目录中的单个旧 `yt-dlp` 文件，再由已签名 APK 的同名资源重新安装；不会清理 Python、FFmpeg 或用户下载数据。
- 不申请短信、通讯录、位置、电话、相机、麦克风或全盘存储权限。
- 网络安全配置禁用明文 HTTP 传输，输入层也拒绝 HTTP 来源链接。
- 下载先写应用暂存目录，成功后通过 MediaStore 提交到系统媒体库。

## 抖音补丁专项审计

- 审计对象为 [IvanaGyro/yt-dlp 提交 `73d89b8`](https://github.com/IvanaGyro/yt-dlp/commit/73d89b829ed21493e832561ffbee20d08c6e662e)。相对其父提交的精确差异只有 `yt_dlp/extractor/tiktok.py`，233 行新增、6 行删除。
- 新增代码实现 SM3、ABogus、匿名 `s_v_web_id` 和 `ttwid` Cookie。新增的固定网络端点只有字节系 `https://ttwid.bytedance.com/ttwid/union/register/`，详情请求仍发往 `https://www.douyin.com/aweme/v1/web/aweme/detail/`；媒体文件随后由接口返回的 Douyin CDN 地址下载。
- 补丁新增代码未发现 `subprocess`、`os.system`、`exec`、`eval`、动态导入、动态代码加载、`ctypes`、套接字直连、本地文件写删或第三方遥测。它不会读取账号 Cookie；生成的匿名 Cookie 仅用于当前 yt-dlp 进程的请求。
- FlowFrame 本地补丁调整 Douyin 作者字段映射、传递签名所用 User-Agent 与 `https://www.douyin.com/` Referer，并从同一份已签名详情响应映射图文元数据；不增加网络目的地或可执行能力。图文图片只采用 `image.url_list`，音频只采用 `music.play_url.url_list`，自定义字段不复制含水印的 `download_url_list`。
- 来源 PR 没有正式代码评审，提交作者标注为 Claude，且对应上游 PR 因贡献/AI 政策与许可问题关闭；这不是上游安全背书。因此本项目固定提交、保存精确补丁、校验构建哈希，并保留同一公开链接的黄金测试。

## 已知限制

- 静态检查不能证明第三方平台在未来不会改变页面或访问限制。
- 抖音的签名算法、接口参数和 CDN 防盗链可能再次变化；固定内核不会静默联网更新，变更必须经重新审计、构建和发版。
- 上游 youtubedl-android 的进程取消对子进程清理存在已知限制；本应用未启用 aria2，并在 Worker 结束路径再次执行清理，但合并阶段仍应在真实设备上重点验证。
- 已在 Android 16 x86_64 模拟器和 Android 16 arm64 实体设备完成覆盖升级、抖音视频/图文、哔哩哔哩视频、网络下载与 MediaStore 落盘回归；不同厂商系统与平台后续变化仍需持续复核。

发现安全问题时，请保留复现链接的脱敏版本、Android 版本、应用版本和任务错误信息。不要提交账号凭据或私密媒体链接。
