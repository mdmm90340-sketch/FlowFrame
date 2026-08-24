# 第三方开源说明

流影 FlowFrame 使用以下关键开源组件：

- [youtubedl-android 0.18.1](https://github.com/yausername/youtubedl-android)，GPL-3.0；Android 端 yt-dlp 封装、Python/QuickJS 运行环境与 FFmpeg 集成。
- [yt-dlp](https://github.com/yt-dlp/yt-dlp)，Unlicense；站点解析与媒体格式选择。APK 固定内置官方 `2026.08.19` 提交 [`3a08beaf031ab68f966401ead017ac81fe8486cf`](https://github.com/yt-dlp/yt-dlp/commit/3a08beaf031ab68f966401ead017ac81fe8486cf)。
- [FFmpeg](https://ffmpeg.org/)，按其构建配置适用 LGPL/GPL；用于合并媒体流和音频提取。随 `youtubedl-android:ffmpeg` 预编译组件分发。
- AndroidX、Jetpack Compose、Material Components，Apache-2.0。
- Kotlin 与 kotlinx.coroutines / kotlinx.serialization，Apache-2.0。

`youtubedl-android 0.18.1` 的原生运行包还包含 CPython 3.12、QuickJS、Mutagen、PyCryptodome 及其 Termux 运行依赖；其 JVM 传递依赖包含 Jackson、Apache Commons IO 与 Apache Commons Compress。固定 yt-dlp 内核生成的 JavaScript 解析组件还引用 Meriyah（ISC）与 Astring（MIT）。这些组件保留各自许可证，详细清单应以固定的上游源码、AAR/POM 元数据及 Gradle 依赖树为准。

本项目固定依赖版本可在 `app/build.gradle.kts` 中复核。Gradle 解析生成的完整传递依赖树可通过以下命令查看：

```powershell
.\gradlew.bat :app:dependencies
```

## 固定抖音兼容补丁

内置内核以官方 yt-dlp `2026.08.19` 为基线，按顺序应用三个可审计文本补丁：

1. `tools/patches/yt-dlp-2026.08.19-douyin-abogus.patch`
   - 来源仓库：[IvanaGyro/yt-dlp](https://github.com/IvanaGyro/yt-dlp)，并参考其注明的 [yt-dlp PR #16182](https://github.com/yt-dlp/yt-dlp/pull/16182) 来源链。
   - 来源提交：[`73d89b829ed21493e832561ffbee20d08c6e662e`](https://github.com/IvanaGyro/yt-dlp/commit/73d89b829ed21493e832561ffbee20d08c6e662e)，对应 [PR #2](https://github.com/IvanaGyro/yt-dlp/pull/2)
   - 来源父提交：`ad9a6f25f69344ebc060f7be223e5c19fc03e9b5`（yt-dlp `2026.06.09`）
   - 原提交精确差异仅修改 `yt_dlp/extractor/tiktok.py`，233 行新增、6 行删除；本项目将同一差异无冲突移植到官方 `2026.08.19`。
   - 补丁文件 SHA-256：`F6EF427BA0E7282694A109D06CA3C51773D676C5AA2B0348ED20639B509FFA5B`
   - 其中 SM3 实现源自 [py-gmssl](https://github.com/py-gmssl/py-gmssl)，MIT；ABogus 实现源自 [f2 `abogus.py`](https://github.com/Johnserf-Seed/f2/blob/main/f2/utils/abogus.py)，作者标注为 JohnserfSeed，Apache-2.0。
2. `tools/patches/yt-dlp-flowframe-douyin-uploader.patch`
   - FlowFrame 本地兼容改动，SHA-256：`B311CF8BCC3B3CA756CC305CB8853BB1EF0F4541EBD63085E50EAA616DAE2B07`。
   - 仅在 Douyin 解析结果中优先把作者昵称写入 `uploader`，并把同一套 User-Agent 与 Douyin Referer 传给媒体下载请求，以适配 youtubedl-android 的 `VideoInfo` 字段和抖音 CDN 防盗链。
3. `tools/patches/yt-dlp-flowframe-douyin-gallery.patch`
   - FlowFrame 1.1.0 图文元数据兼容改动，SHA-256：`1CCEE6377AF996D3928724C6F6B0BE00B1EAF937E9FFDCEC19BB74DA9AEB79D9`。
   - 让 Douyin 解析器同时识别 `/video/` 与 `/note/`，并从同一份已签名 `aweme_detail` 生成 JSON 可序列化的 `flowframe_gallery` v1。图片镜像只复制 `image.url_list`，音频镜像只复制 `music.play_url.url_list`；不会复制含水印地址的 `download_url_list`。

生成的 `app/src/main/res/raw/ytdlp` 为内核 `2026.08.19-flowframe.douyin.73d89b8.3`，大小 `2,976,260` 字节，SHA-256 为 `E88DBA1F25813F43E9292B676F8D03186757CA0D2011DF4CE54ECD60810019E8`。可复现构建入口为：

```powershell
.\tools\build-patched-ytdlp.ps1
```

构建脚本固定官方提交和三个补丁哈希，不在应用运行时下载或更新解析代码。MIT、Apache-2.0、Unlicense 与 GPL-3.0 完整文本随 APK 的 `assets/open_source/` 分发。

本项目及 GPL-3.0 完整文本随源码和 APK 一并提供。上游商标归各自权利人所有；本项目名称和图标不表示得到抖音、哔哩哔哩或上游项目的官方认可。

## 对应源码与构建来源

- FlowFrame 自身源码、固定补丁和构建脚本：与 APK 同一 GitHub Release 的 `v1.1.2` 源码归档。
- youtubedl-android 0.18.1：[固定标签源码](https://github.com/yausername/youtubedl-android/tree/0.18.1) 与 [FFmpeg 构建说明](https://github.com/yausername/youtubedl-android/blob/0.18.1/BUILD_FFMPEG.md)。
- yt-dlp：官方提交 [`3a08beaf`](https://github.com/yt-dlp/yt-dlp/commit/3a08beaf031ab68f966401ead017ac81fe8486cf) 加仓库中的三个补丁；`tools/build-patched-ytdlp.ps1` 可重新生成内核。
- FFmpeg 7.1.1：[官方源码与许可证](https://ffmpeg.org/releases/)；本 APK 使用 youtubedl-android 0.18.1 提供的 GPLv3 原生构建。
- Android/Kotlin 与 JVM 依赖的精确解析版本可通过 `.\gradlew.bat :app:dependencies` 生成；源码入口分别由 Maven POM 指向各上游仓库。

如果某个上游源码链接失效，请通过 GitHub Issue 提醒维护者；仓库所有者会按 GPL-3.0 的对应源码义务补充可获得的归档。
