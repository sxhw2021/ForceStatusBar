# GitHub Actions 快速开始

本指南帮助你在 5 分钟内在 GitHub 上自动构建 LSPosed 模块。

## 准备工作

- 一个 GitHub 账号
- 本项目代码

## 步骤 1：创建 GitHub 仓库

1. 登录 [GitHub](https://github.com)
2. 点击右上角 **+** → **New repository**
3. 填写仓库信息：
   - **Repository name**: `ForceStatusBar`（或其他名称）
   - **Description**: `强制显示状态栏的 LSPosed 模块`
   - **Visibility**: Public（推荐）或 Private
   - 勾选 **Add a README file**（可选）
4. 点击 **Create repository**

## 步骤 2：上传代码

### 方式一：使用命令行

```bash
# 在项目根目录执行
cd ForceStatusBar

# 初始化 git
git init

# 添加所有文件
git add .

# 提交
git commit -m "Initial commit: ForceStatusBar LSPosed Module"

# 连接到远程仓库（替换为你的用户名和仓库名）
git remote add origin https://github.com/YOUR_USERNAME/ForceStatusBar.git

# 推送到 GitHub
git push -u origin main
```

### 方式二：使用 GitHub Desktop

1. 下载并安装 [GitHub Desktop](https://desktop.github.com/)
2. File → Add local repository
3. 选择 `ForceStatusBar` 文件夹
4. 填写提交信息，点击 Commit
5. 点击 Publish repository

### 方式三：直接上传文件

1. 在仓库页面点击 **Add file** → **Upload files**
2. 拖拽或选择所有项目文件
3. 点击 **Commit changes**

## 步骤 3：触发自动构建

上传代码后，GitHub Actions 会自动开始构建：

1. 访问仓库页面
2. 点击 **Actions** 标签
3. 查看正在运行的 workflow
4. 等待构建完成（约 2-3 分钟）

## 步骤 4：下载 APK

### 从 Actions 下载

1. 在 Actions 页面点击最新的 workflow
2. 滚动到底部找到 **Artifacts**
3. 下载 `ForceStatusBar-Debug`
4. 解压 ZIP 文件即可获得 APK

### 创建 Release 版本

```bash
# 创建标签
git tag -a v1.0.0 -m "Release version 1.0.0"

# 推送标签
git push origin v1.0.0
```

推送标签后，GitHub Actions 会自动：
- 构建 Debug 和 Release APK
- 创建 GitHub Release
- 上传 APK 到 Release 页面

然后访问仓库的 **Releases** 页面即可下载。

## 可选：配置签名

如果你想发布已签名的 APK，需要配置签名密钥：

### 1. 生成密钥

```bash
# 使用 keytool 生成（需要 JDK）
keytool -genkey -v -keystore release.keystore -alias forcestatusbar -keyalg RSA -keysize 2048 -validity 10000

# 输入密码和信息后，转换为 Base64
# Linux/Mac:
base64 -i release.keystore -o release.keystore.base64

# Windows PowerShell:
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -Encoding ASCII release.keystore.base64
```

### 2. 添加到 GitHub Secrets

1. 打开仓库页面
2. 点击 **Settings** → **Secrets and variables** → **Actions**
3. 点击 **New repository secret**
4. 添加以下 secrets：

| 名称 | 值 |
|------|-----|
| `SIGNING_KEY` | release.keystore.base64 文件内容 |
| `ALIAS` | 你的密钥别名（如 forcestatusbar） |
| `KEY_STORE_PASSWORD` | keystore 密码 |
| `KEY_PASSWORD` | 密钥密码 |

### 3. 创建 Release

配置完成后，推送新的 tag 即可自动构建签名版 APK。

## 查看构建状态

### 构建成功 ✅

- Actions 页面显示绿色对勾
- Artifacts 中可下载 APK
- Release 页面显示新版本

### 构建失败 ❌

- 点击失败的 workflow
- 查看具体步骤的日志
- 常见错误：
  - 网络问题导致 Gradle 依赖下载失败 → 重试
  - Gradle 版本不兼容 → 检查配置
  - 签名密钥错误 → 检查 secrets 配置

## 更新模块

修改代码后重新推送：

```bash
# 修改文件...

# 提交更改
git add .
git commit -m "更新：添加新功能"
git push origin main
```

GitHub Actions 会自动重新构建。

## 常见问题

### Q: 构建成功但 APK 在哪里？

**A**: 
- 在 Actions 页面点击构建记录
- 滚动到底部 **Artifacts** 部分
- 下载并解压 ZIP 文件

### Q: 如何更新到最新版本？

**A**:
```bash
git pull origin main
```

### Q: 可以修改模块信息吗？

**A**: 可以，编辑以下文件：
- `app/src/main/res/values/strings.xml` - 应用名称
- `app/build.gradle` - 版本号和包名
- `app/src/main/AndroidManifest.xml` - 模块描述

### Q: 需要付费吗？

**A**: GitHub Actions 对公开仓库免费，私有仓库有免费额度。

## 下一步

- 阅读 [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md) 了解详细配置
- 阅读 [QUICK_START.md](QUICK_START.md) 了解使用方法
- 查看项目代码了解实现原理

## 获取帮助

遇到问题？
1. 查看 [GitHub Actions 文档](https://docs.github.com/cn/actions)
2. 检查 workflow 日志中的错误信息
3. 提交 Issue 寻求帮助
