# GitHub Actions 自动构建配置

本模块支持使用 GitHub Actions 自动构建 APK，无需本地配置 Android 开发环境。

## 功能特性

- ✅ 自动下载 Xposed API 依赖
- ✅ 自动构建 Debug APK
- ✅ 自动构建并签名 Release APK
- ✅ 自动创建 GitHub Release
- ✅ 每次推送自动构建测试

## 快速开始

### 1. 准备项目文件

项目已包含所有必要文件：
- `.github/workflows/build.yml` - GitHub Actions 工作流
- `build.gradle` - 项目构建配置
- `app/build.gradle` - 模块构建配置

### 2. 创建 GitHub 仓库

1. 访问 [GitHub](https://github.com/new) 创建新仓库
2. 仓库名称建议：`ForceStatusBar`
3. 设为 Public 或 Private 均可

### 3. 推送代码

```bash
# 初始化 git（如果尚未初始化）
git init

# 添加所有文件
git add .

# 提交
git commit -m "Initial commit: ForceStatusBar LSPosed Module"

# 连接到远程仓库（替换 YOUR_USERNAME 和 YOUR_REPO）
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git

# 推送到 GitHub
git push -u origin main
```

### 4. 配置签名密钥（可选，用于发布版本）

如需自动签名 Release APK，需要配置密钥：

#### 生成签名密钥

```bash
# 生成 keystore
keytool -genkey -v -keystore release.keystore -alias forcestatusbar -keyalg RSA -keysize 2048 -validity 10000

# 转换为 Base64（Linux/Mac）
base64 -i release.keystore -o release.keystore.base64

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -Encoding ASCII release.keystore.base64
```

#### 添加到 GitHub Secrets

1. 访问仓库页面：`https://github.com/YOUR_USERNAME/YOUR_REPO`
2. 点击 **Settings** → **Secrets and variables** → **Actions**
3. 点击 **New repository secret**，添加以下 secrets：

| Secret 名称 | 说明 | 示例 |
|------------|------|------|
| `SIGNING_KEY` | Base64 编码的 keystore 文件内容 | `MIIKqAIBAzC...` |
| `ALIAS` | 密钥别名 | `forcestatusbar` |
| `KEY_STORE_PASSWORD` | Keystore 密码 | `your_password` |
| `KEY_PASSWORD` | 密钥密码（通常与 keystore 密码相同） | `your_password` |

### 5. 触发构建

#### 自动触发

- **推送代码到 main 分支** → 自动构建 Debug APK
- **推送 tag（如 v1.0.0）** → 自动构建 Debug + Release APK，并创建 Release

#### 手动触发

1. 访问仓库 Actions 页面
2. 选择 **Build LSPosed Module** 工作流
3. 点击 **Run workflow**

### 6. 创建 Release

```bash
# 创建带注释的 tag
git tag -a v1.0.0 -m "Release version 1.0.0"

# 推送到 GitHub
git push origin v1.0.0
```

推送后，GitHub Actions 会自动：
1. 构建 Debug 和 Release APK
2. 签名 Release APK
3. 创建 GitHub Release
4. 上传 APK 文件到 Release

## 下载构建产物

### 方式一：GitHub Actions Artifacts

1. 访问仓库 Actions 页面
2. 点击最新的 workflow 运行记录
3. 滚动到 **Artifacts** 部分
4. 下载 `ForceStatusBar-Debug` 或 `ForceStatusBar-Release`

### 方式二：GitHub Release

1. 访问仓库 Releases 页面
2. 下载对应版本的 APK

## 工作流详细说明

### 触发条件

```yaml
on:
  push:
    branches: [ main, master ]
    tags: [ 'v*' ]
  pull_request:
    branches: [ main, master ]
  workflow_dispatch:  # 手动触发
```

### 构建步骤

1. **检出代码** - 使用 `actions/checkout@v4`
2. **设置 JDK** - 使用 JDK 17 (Temurin 发行版)
3. **下载 Xposed API** - 自动从 Maven Central 下载
4. **构建 Debug APK** - 运行 `./gradlew assembleDebug`
5. **构建 Release APK** (仅 tag) - 运行 `./gradlew assembleRelease`
6. **签名 Release APK** (仅 tag) - 使用配置的密钥签名
7. **上传产物** - 使用 `actions/upload-artifact@v4`
8. **创建 Release** (仅 tag) - 使用 `softprops/action-gh-release@v1`

## 故障排除

### 构建失败：找不到 Xposed API

**错误信息**：
```
Could not find de.robv.android.xposed:api:82
```

**解决方案**：
工作流会自动下载 Xposed API JAR 文件，无需手动添加。如果失败，检查网络连接或手动下载后放入 `app/libs/` 目录。

### 签名失败

**错误信息**：
```
Error: Input required and not supplied: signingKeyBase64
```

**解决方案**：
未配置签名密钥时，Release 构建会跳过签名步骤。如需签名，请按照上文配置 GitHub Secrets。

### 构建成功但 APK 安装失败

**原因**：
- Debug APK 使用测试签名，可能需要卸载旧版本
- 未启用 LSPosed 模块

**解决方案**：
```bash
# 卸载旧版本
adb uninstall com.example.forcestatusbar

# 安装新版本
adb install app-debug.apk
```

## 自定义配置

### 修改 Gradle 版本

编辑 `gradle/wrapper/gradle-wrapper.properties`：
```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.0-bin.zip
```

### 修改 JDK 版本

编辑 `.github/workflows/build.yml`：
```yaml
- name: Set up JDK 17
  uses: actions/setup-java@v4
  with:
    java-version: '17'  # 改为 '11' 或 '21'
```

### 修改最低 SDK 版本

编辑 `app/build.gradle`：
```gradle
defaultConfig {
    minSdk 26  # 改为需要的版本
    targetSdk 34
}
```

## 安全注意事项

⚠️ **永远不要将签名密钥提交到 Git 仓库！**

正确的做法：
- ✅ 将 keystore 文件添加到 `.gitignore`
- ✅ 使用 GitHub Secrets 存储密钥信息
- ✅ 仅在 GitHub Actions 中使用密钥

错误的做法：
- ❌ 将 `.keystore` 或 `.jks` 文件提交到仓库
- ❌ 在代码中硬编码密钥密码
- ❌ 在日志中输出密钥信息

## 参考链接

- [GitHub Actions 文档](https://docs.github.com/cn/actions)
- [setup-java Action](https://github.com/actions/setup-java)
- [sign-android-release Action](https://github.com/r0adkll/sign-android-release)
- [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
