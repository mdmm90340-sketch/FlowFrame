# 开发指南

这份文档用于在新电脑或下次继续开发时快速恢复 FlowFrame 的本地环境。

## 环境

- JDK 17
- Android Studio 或命令行 Android SDK
- Android SDK Platform 35
- Android Build Tools 35.0.0
- Git
- Python 3（仅在重建固定 yt-dlp 内核时需要）

项目使用 Gradle Wrapper 8.10.2 和 Android Gradle Plugin 8.8.2，不需要全局安装 Gradle。`minSdk` 为 24，`targetSdk` 为 35。

## 首次构建

1. 克隆仓库并用 Android Studio 打开根目录。
2. 让 Android Studio 创建 `local.properties`，或在其中配置本机 Android SDK 路径。该文件不得提交。
3. 在 Windows PowerShell 运行：

```powershell
.\gradlew.bat testDebugUnitTest lintRelease assembleDebug
```

Linux/macOS 使用 `./gradlew`。Debug APK 位于 `app/build/outputs/apk/debug/`。

默认 APK 只包含 `arm64-v8a`。为 x86_64 Android 模拟器构建：

```powershell
.\gradlew.bat assembleDebug -Pflowframe.testAbi=x86_64
```

安装或覆盖安装测试包：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

切换签名不同的 Debug/Release 包时，Android 不允许直接覆盖。删除应用会同时清除任务历史，因此在需要保留数据的设备上不要为了省事执行卸载或清除数据。

## 常用验证

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintRelease
.\gradlew.bat assembleDebug
```

涉及真实下载时，应同时验证：解析预览、输出尺寸和时长、完整解码、MediaStore 文件、取消/重试、临时文件清理、任务历史以及日志脱敏。不要把真实下载内容和设备抓取提交到仓库。

## 固定解析内核

APK 内的 `app/src/main/res/raw/ytdlp` 是固定提交和三个文本补丁生成的确定性 zipapp。普通 Android 开发可以直接使用仓库内文件；只有修改基线或补丁时才需要重建：

```powershell
.\tools\build-patched-ytdlp.ps1
```

脚本会验证补丁哈希、拉取固定 yt-dlp 提交、依次应用补丁并生成内核。重建后必须核对输出 SHA-256，并同步更新：

- `tools/yt-dlp-kernel.json`
- `tools/build-patched-ytdlp.ps1`
- `app/src/main/java/com/flowframe/app/core/engine/BundledYtDlpInstaller.kt`
- `THIRD_PARTY_NOTICES.md`
- `app/src/main/assets/open_source/NOTICE.txt`

不得把上游临时仓库、Cookie 或抓取页面加入源码。

## 目录

- `app/src/main/java/com/flowframe/app/core/`：URL、模型、解析下载引擎、图文下载与合成。
- `app/src/main/java/com/flowframe/app/data/`：设置、任务存储和 WorkManager 入队。
- `app/src/main/java/com/flowframe/app/storage/`：MediaStore 原子发布。
- `app/src/main/java/com/flowframe/app/ui/`：Compose 界面、主题和 UI 状态映射。
- `app/src/main/java/com/flowframe/app/worker/`：后台下载、并发门和通知。
- `app/src/test/`：JVM 回归测试。
- `tools/`：固定内核 manifest、补丁与可复现构建脚本。

更完整的数据流和模块职责见 [ARCHITECTURE.md](ARCHITECTURE.md)。正式签名和 GitHub Release 步骤见 [RELEASING.md](RELEASING.md)；正式密钥与已填写的签名配置应保存在工作区外。
