# 发布正式版指南

## 快速发布

在项目目录运行：

```bash
./release-v1.0.0.sh
```

或者手动执行：

```bash
cd ForceStatusBar

# 1. 添加更改
git add .

# 2. 提交
git commit -m "Release v1.0.0"

# 3. 推送到 main
git push origin main

# 4. 创建标签
git tag -a v1.0.0 -m "Release version 1.0.0"

# 5. 推送标签（触发 GitHub Actions Release 构建）
git push origin v1.0.0
```

## 等待构建完成

推送标签后，GitHub Actions 会自动：
1. 构建 Debug APK
2. 构建 Release APK
3. 创建 GitHub Release
4. 上传 APK 到 Release 页面

## 下载 Release

几分钟后访问：
https://github.com/sxhw2021/ForceStatusBar/releases

你会看到：
- `app-debug.apk` - 调试版本
- `app-release-unsigned.apk` - 发布版本（未签名）

## 签名 APK（可选）

如果需要签名版本，可以：

1. 本地签名后上传
2. 配置 GitHub Secrets 自动签名

### 配置自动签名

1. 生成签名密钥：
```bash
keytool -genkey -v -keystore release.keystore -alias forcestatusbar -keyalg RSA -keysize 2048 -validity 10000
```

2. 转换为 Base64：
```bash
# Linux/Mac
base64 -i release.keystore -o release.keystore.base64

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -Encoding ASCII release.keystore.base64
```

3. 在 GitHub 仓库设置中添加 Secrets：
   - `SIGNING_KEY`: release.keystore.base64 的内容
   - `ALIAS`: forcestatusbar
   - `KEY_STORE_PASSWORD`: 你的密码
   - `KEY_PASSWORD`: 你的密码

配置后，推送新 tag 会自动构建签名版 APK。

## 版本号管理

下次发布时：

1. 更新 `app/build.gradle` 中的版本号：
```gradle
versionCode 2
versionName "1.0.1"
```

2. 修改 `release-v1.0.0.sh` 中的版本号

3. 推送新 tag：
```bash
git tag -a v1.0.1 -m "Release version 1.0.1"
git push origin v1.0.1
```
