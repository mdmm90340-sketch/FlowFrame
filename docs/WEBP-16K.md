# WebP 1.6.0 原生库重建记录

FlowFrame 保持 `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1` 的 FFmpeg 版本和其他原生组件，仅替换其中 WebP 1.6.0 的五个共享库。原 AAR 的 `arm64-v8a` 和 `x86_64` 包均有这五个库的 `PT_LOAD` 对齐为 4096：`libsharpyuv.so`、`libwebpdecoder.so`、`libwebp.so`、`libwebpdemux.so`、`libwebpmux.so`。这些库位于嵌套压缩包 `jni/<abi>/libffmpeg.zip.so` 的 `usr/lib/` 内，单独检查 APK 的 ZIP 对齐无法发现这一问题。

## 来源和变更范围

- [WebP 官方 v1.6.0 源码](https://github.com/webmproject/libwebp/tree/v1.6.0)，固定 commit `4fa21912338357f89e4fd51cf2368325b59e9bd9`。源码内容没有补丁，使用官方 CMake 构建。
- [源码压缩包](https://github.com/webmproject/libwebp/archive/refs/tags/v1.6.0.tar.gz) SHA-256 为 `93a852c2b3efafee3723efd4636de855b46f9fe1efddd607e1f42f60fc8f2136`。[Termux 官方配方](https://github.com/termux/termux-packages/blob/master/packages/libwebp/build.sh)提供对应版本、来源和校验和，可交叉核对。
- [原 FFmpeg AAR](https://repo.maven.apache.org/maven2/io/github/junkfood02/youtubedl-android/ffmpeg/0.18.1/ffmpeg-0.18.1.aar) SHA-256 为 `0a87ffa6cf912b0fe76c1a99b9107f543ee2f247935fae2c71f0822eb7bc5f49`。本轮未采用未发布 PR 的原生二进制，也未升级 yt-dlp。
- 工具链固定 Android NDK `28.2.13676358`、CMake `3.22.1`，目标最低 API 24，两种 ABI 均从源码编译。显式使用 `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`，并用 NDK `llvm-strip --strip-unneeded` 去除调试符号。

`tools/webp-16k.json` 固定输入；`native/webp-1.6.0/manifest.json` 记录全部十个最终库的 SHA-256、SONAME、动态依赖、导出符号数量和对齐检查结果。同目录保留官方 `COPYING`、`PATENTS`、`AUTHORS`。正常应用构建直接使用这些可重新生成的库，无需在构建机器安装 NDK。

## 重新生成

需要 Windows PowerShell、Python 3.9 或以上，以及上述版本的 Android NDK/CMake。在仓库根目录运行：

```powershell
.\tools\rebuild-webp-16k.ps1 -SdkRoot $env:ANDROID_HOME
```

可用 `-Python` 指定 Python 程序，`-FfmpegAar` 指定已下载的原 AAR。脚本仍会核对输入 SHA-256。中间产物默认放在忽略提交的 `build/native-webp`；`-BuildDirectory` 和 `-OutputDirectory` 可用于在独立目录重建比较。脚本只接受校验和匹配的官方源码，不修改 ELF 头。它以 Release 配置启用 SIMD、线程和 16 位颜色通道交换，关闭命令行工具，保留五个共享库的无版本 SONAME。

`tools/verify_webp_16k.py` 使用 Python 标准库检查 ELF，全部十个库通过后才覆盖最终输出：

1. 架构和 SONAME 与原 AAR 一致。
2. 原库的动态导出符号在新库中全部保留；新增依赖限于这五个库和 Android 系统 `libc`、`libm`、`libdl`。
3. 所有 `PT_LOAD` 对齐至少 16384，文件偏移与虚拟地址在 16 KiB 下同余，GNU RELRO 结束地址按 16 KiB 对齐。
4. 去除调试符号后重新核对动态导出和 SHA-256，拒绝包含本机构建目录路径的产物。

## 打包和升级

Gradle 在原 AAR 校验成功后，生成仅替换上述库的本地 AAR。原嵌套 ZIP 包含 Unix 符号链接；重打包必须保留其 Unix mode、链接内容和其他条目。上游 [ZipUtils](https://github.com/yausername/youtubedl-android/blob/0.18.1/common/src/main/java/com/yausername/youtubedl_common/utils/ZipUtils.kt) 会根据 `isUnixSymlink` 创建实际符号链接，丢失 ZIP 外部属性会导致库加载失败。

上游 [FFmpeg 初始化](https://github.com/yausername/youtubedl-android/blob/0.18.1/ffmpeg/src/main/java/com/yausername/ffmpeg/FFmpeg.kt) 使用压缩包文件长度判断缓存版本。FlowFrame 的初始化流程因此还须以 manifest 中的 SHA-256 校验解压后的五个库；旧缓存不匹配时，仅清理应用私有的 `noBackupFilesDir/youtubedl-android/packages/ffmpeg`，在进程首次调用 `FFmpeg.init` 前促使重提取，并在其后复核。不能删除用户媒体或依赖版本字符串判断已经更新。

## 验证边界

打包后可执行以下独立检查（`original.aar` 为上述校验和匹配的原包）：

```powershell
python tools/verify_ffmpeg_repack.py original.aar app/build/generated/native/ffmpeg-0.18.1-flowframe16k.aar native/webp-1.6.0/manifest.json
python tools/verify_apk_native.py app/build/outputs/apk/release/app-release.apk
```

第一项检查条目集合、全部原始内容和 Unix 属性，每种架构只允许 5 个库内容改变；本次每种架构的 55 个符号链接均完整保留。第二项递归进入压缩的 FFmpeg/Python payload，分别核对所有 ELF 的 LOAD 对齐、偏移同余和 GNU_RELRO 结束地址；缺失 RELRO 单列为未完成验证，格式/容器错误单列，任何未通过的结果均返回非零状态。字段 `all_static_alignment_checks_passed` 只表示静态检查，不能代表运行支持。

本记录确认两种 ABI 的十个重编 WebP 库通过上述静态检查，并用 NDK 的 `llvm-readelf` 独立交叉核对了两种架构的 `libwebp.so`。**完整 APK 静态检查仍失败**：arm64 的 257 个 ELF 中有 192 个 RELRO 结束地址错位，x86_64 的 259 个中有 188 个错位；全部 LOAD 对齐及 APK ZIP 检查通过。未改动的上游组件需要进一步从源码重建，不能把 WebP 修复称为全包 16 KiB 修复。

最终 x86_64 正式包已在普通 4 KiB 的 API 24/35 及官方 API 35 Experimental 16 KiB r5 镜像（`getconf PAGE_SIZE=16384`）完成启动、五库 SHA、WebP 编解码与 MP4 合成/完整解码。16 KiB 的这条执行路径通过，不消除其他库的静态缺口，也不代表 arm64 真机验收。[Android 官方说明](https://developer.android.com/guide/practices/page-sizes#relro)要求同时核对 ELF、RELRO、打包及运行时的页大小假设。详见 [本次验收报告](VERIFICATION-1.2.0.md)。
