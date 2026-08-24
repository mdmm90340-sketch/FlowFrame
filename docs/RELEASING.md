# 发布指南

本指南面向仓库维护者。不要把正式签名文件或密码放进 Git、Issue、CI 日志或 Release。

## 1. 更新版本

至少同步检查：

- `app/build.gradle.kts` 中的 `versionCode` 与 `versionName`
- `CHANGELOG.md`
- `README.md` 的当前稳定版
- `app/src/main/assets/open_source/NOTICE.txt`
- 对应版本的 GitHub Release 说明

如果解析内核发生变化，还要更新内核 manifest、安装器常量、补丁哈希和第三方说明。

## 2. 配置本地签名

在工作区外建立私密目录，把 `keystore.properties.example` 复制进去并填写。`storeFile` 的相对路径按这份配置文件所在目录解析：

```properties
storeFile=path/to/your-release.jks
storePassword=replace-me
keyAlias=replace-me
keyPassword=replace-me
```

通过环境变量告诉 Gradle 配置文件的位置：

```powershell
$env:FLOWFRAME_SIGNING_PROPERTIES = "C:\Users\your-name\Documents\FlowFrame-private\keystore.properties"
```

根目录 `keystore.properties` 仍向后兼容并已被 `.gitignore` 排除，但推荐把配置和密钥都放在工作区外。发布密钥应另行加密备份；丢失后无法为已安装用户提供可覆盖升级的同签名 APK。

## 3. 构建与验证

```powershell
.\gradlew.bat testDebugUnitTest lintRelease assembleRelease
```

确认输出只包含 `arm64-v8a`，并使用 Android Build Tools 验证签名和 16 KiB 对齐：

```powershell
$FlowFrameBuildTools = "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0"
& "$FlowFrameBuildTools\apksigner.bat" verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
& "$FlowFrameBuildTools\zipalign.exe" -c -P 16 -v 4 app\build\outputs\apk\release\app-release.apk
Get-FileHash -Algorithm SHA256 app\build\outputs\apk\release\app-release.apk
```

随后在 arm64 实体设备使用 `adb install -r` 覆盖安装，完成受影响平台、任务状态、媒体打开/分享、取消/重试和历史兼容回归。

## 4. 提交与标签

确保工作区只包含计划发布的源码和文档，CI 通过后创建带说明的标签。版本号示例：

```powershell
git tag -a v1.1.2 -m "FlowFrame 1.1.2"
git push origin main
git push origin v1.1.2
```

## 5. GitHub Release

APK 放在 GitHub Release，不放进 Git 历史。Release 至少上传 APK、SHA-256 文件和版本说明。

```powershell
gh release create v1.1.2 `
  dist\FlowFrame-1.1.2-arm64-release.apk `
  dist\FlowFrame-1.1.2-SHA256SUMS.txt `
  dist\FlowFrame-1.1.2-README.md `
  --title "FlowFrame 1.1.2" `
  --notes-file docs\releases\1.1.2.md `
  --verify-tag
```

上面代码块中每行末尾的反引号是 PowerShell 续行符。发布后从 GitHub 下载 APK，在全新目录克隆源码，再次核对版本、SHA-256、构建说明和 Release 链接。
