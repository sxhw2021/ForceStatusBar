# 查找 release.keystore.base64 文件

## 文件生成位置

执行转换命令后，`release.keystore.base64` 文件会和 `release.keystore` 在同一个目录。

## 不同系统的查找方法

### Windows

**方法 1：直接在命令行查看内容**
```powershell
# 在 PowerShell 中执行转换后，直接显示内容
type release.keystore.base64
```

**方法 2：使用文件资源管理器**
1. 打开文件资源管理器
2. 在你执行命令的文件夹中查找
3. 文件名：`release.keystore.base64`
4. 用记事本打开，复制全部内容

**方法 3：使用 Notepad 直接打开**
```powershell
notepad release.keystore.base64
```

### Linux/Mac

**方法 1：直接查看内容**
```bash
cat release.keystore.base64
```

**方法 2：使用文本编辑器**
```bash
# 使用 nano
nano release.keystore.base64

# 或使用 vim
vim release.keystore.base64
```

## 完整流程示例

### Windows PowerShell 完整流程：

```powershell
# 第 1 步：进入项目目录（如果你在项目目录执行）
cd C:\Users\你的用户名\ForceStatusBar

# 第 2 步：生成密钥
# 执行后会要求输入密码，请记住！
keytool -genkey -v -keystore release.keystore -alias forcestatusbar -keyalg RSA -keysize 2048 -validity 10000

# 第 3 步：转换为 Base64
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -Encoding ASCII release.keystore.base64

# 第 4 步：查看文件是否生成
ls release.keystore*

# 第 5 步：查看内容（这就是要复制到 GitHub 的）
type release.keystore.base64
```

输出示例：
```
MIIKqAIBAzC...（非常长的一串字符，可能有几百行）...z8n5SA==
```

### 找不到文件？

**检查当前目录：**
```powershell
# Windows
Get-Location

# Linux/Mac
pwd
```

**搜索文件：**
```powershell
# Windows（在 C 盘搜索）
Get-ChildItem -Path C:\ -Filter "release.keystore.base64" -Recurse -ErrorAction SilentlyContinue

# Linux/Mac
find ~ -name "release.keystore.base64" 2>/dev/null
```

## 复制到 GitHub 的方法

### 方法 1：命令行复制（推荐）

**Windows PowerShell：**
```powershell
# 复制到剪贴板
Get-Content release.keystore.base64 | Set-Clipboard

# 然后直接粘贴到 GitHub Secrets
```

**Linux：**
```bash
# 使用 xclip（需要安装）
cat release.keystore.base64 | xclip -selection clipboard

# 或使用 wl-copy（Wayland）
cat release.keystore.base64 | wl-copy
```

**Mac：**
```bash
cat release.keystore.base64 | pbcopy
```

### 方法 2：手动复制

1. 用记事本/文本编辑器打开 `release.keystore.base64`
2. 按 `Ctrl+A` 全选
3. 按 `Ctrl+C` 复制
4. 到 GitHub Secrets 页面按 `Ctrl+V` 粘贴

## 常见问题

### Q: 执行了转换命令但找不到文件？
**A:** 检查是否在正确的目录执行。查看当前目录：
```powershell
Get-Location  # Windows
pwd           # Linux/Mac
```

### Q: 文件内容为空？
**A:** 可能是转换失败。检查原始文件是否存在：
```powershell
ls release.keystore
```
如果不存在，需要重新生成密钥。

### Q: 内容太长，复制不全？
**A:** 使用命令行复制到剪贴板的方法，确保复制完整内容。

## 检查文件内容是否正确

Base64 内容应该：
- 以字母和数字开头（如 `MII`）
- 包含很长的随机字符
- 以 `==` 或 `=` 结尾
- 中间可能有换行，也可能没有

示例（缩短版）：
```
MIIKqAIBAzC...+几百个字符...+z8n5SA==
```

如果内容看起来不像这样，可能是生成失败了。
