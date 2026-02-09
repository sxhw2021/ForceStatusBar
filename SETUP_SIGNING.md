# 配置自动签名指南

## 步骤 1：生成签名密钥

在你的电脑上运行以下命令：

### Windows (PowerShell):
```powershell
# 生成密钥
keytool -genkey -v -keystore release.keystore -alias forcestatusbar -keyalg RSA -keysize 2048 -validity 10000

# 输入密码时请记住这个密码！
# 然后会要求填写信息，可以随便填或按回车使用默认值

# 转换为 Base64
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -Encoding ASCII release.keystore.base64

# 查看内容
type release.keystore.base64
```

### Linux/Mac:
```bash
# 生成密钥
keytool -genkey -v -keystore release.keystore -alias forcestatusbar -keyalg RSA -keysize 2048 -validity 10000

# 转换为 Base64
base64 -i release.keystore -o release.keystore.base64

# 查看内容
cat release.keystore.base64
```

## 步骤 2：添加到 GitHub Secrets

1. 打开你的 GitHub 仓库页面
2. 点击 **Settings** → **Secrets and variables** → **Actions**
3. 点击 **New repository secret**，依次添加以下 4 个 secrets：

### Secret 1: SIGNING_KEY
- **Name**: `SIGNING_KEY`
- **Value**: 复制 `release.keystore.base64` 文件的完整内容（很长的一串字符）

### Secret 2: ALIAS
- **Name**: `ALIAS`
- **Value**: `forcestatusbar`

### Secret 3: KEY_STORE_PASSWORD
- **Name**: `KEY_STORE_PASSWORD`
- **Value**: 你生成密钥时设置的密码

### Secret 4: KEY_PASSWORD
- **Name**: `KEY_PASSWORD`
- **Value**: 通常与 KEY_STORE_PASSWORD 相同

## 步骤 3：重新发布

配置完成后，推送一个新的 tag 触发构建：

```bash
cd ForceStatusBar

# 创建新的 tag（版本号加 1）
git tag -a v1.0.1 -m "Release v1.0.1 - 签名版"

# 推送 tag
git push origin v1.0.1
```

## 步骤 4：下载签名版

等待几分钟后访问：
https://github.com/sxhw2021/ForceStatusBar/releases

你会看到新的 Release `v1.0.1`，其中包含：
- `app-release-signed.apk` - 已签名的正式版 ✅

## 重要提示

⚠️ **请妥善保管 `release.keystore` 文件！**
- 这是你的签名密钥，丢失后无法更新应用
- 建议备份到安全的地方（如云盘、U盘）
- 不要提交到 Git 仓库！

## 一键配置脚本（Windows）

创建 `setup-signing.bat`：

```batch
@echo off
echo =======================================
echo  生成签名密钥
echo =======================================
echo.

keytool -genkey -v -keystore release.keystore -alias forcestatusbar -keyalg RSA -keysize 2048 -validity 10000

echo.
echo =======================================
echo  转换为 Base64
echo =======================================
echo.

powershell -Command "[Convert]::ToBase64String([IO.File]::ReadAllBytes('release.keystore')) | Out-File -Encoding ASCII release.keystore.base64"

echo.
echo =======================================
echo  完成！
echo =======================================
echo.
echo 请复制 release.keystore.base64 的内容到 GitHub Secrets
echo.
pause
```

运行后按照提示操作即可。
