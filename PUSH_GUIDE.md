# 推送代码到 GitHub

## 问题原因

GitHub Actions 执行的是旧版本的 workflow 文件，因为你没有将修改推送到 GitHub 仓库。

## 解决步骤

在 ForceStatusBar 项目目录中执行以下命令：

### 1. 检查修改

```bash
cd ForceStatusBar
git status
```

你应该能看到类似这样的输出：
```
modified:   .github/workflows/build.yml
modified:   settings.gradle
modified:   README.md
...
```

### 2. 添加所有修改

```bash
git add .
```

### 3. 提交修改

```bash
git commit -m "Fix: 使用 Xposed Maven 仓库自动下载依赖，移除手动下载步骤"
```

### 4. 推送到 GitHub

```bash
git push origin main
```

或者如果你使用的是 master 分支：

```bash
git push origin master
```

### 5. 验证推送

访问你的 GitHub 仓库页面，确认文件已更新：
https://github.com/sxhw2021/ForceStatusBar

## 检查 Workflow 文件

推送后，访问以下链接确认 `.github/workflows/build.yml` 文件中没有手动下载 Xposed API 的步骤：
https://github.com/sxhw2021/ForceStatusBar/blob/main/.github/workflows/build.yml

**正确的 workflow 文件不应该包含以下内容：**
```yaml
- name: Download Xposed API
  run: |
    mkdir -p app/libs
    wget https://repo1.maven.org/maven2/...
```

**应该包含：**
```yaml
- name: Build with Gradle
  run: ./gradlew assembleDebug
```

## 触发新的构建

推送代码后，GitHub Actions 会自动触发新的构建。你可以在以下页面查看：
https://github.com/sxhw2021/ForceStatusBar/actions

## 如果推送失败

### 错误："fatal: not a git repository"

需要初始化 git 仓库：
```bash
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/sxhw2021/ForceStatusBar.git
git push -u origin main
```

### 错误："Permission denied"

检查 GitHub 权限或重新配置 git 凭据。

### 错误："Updates were rejected"

先拉取远程更改：
```bash
git pull origin main --rebase
git push origin main
```
