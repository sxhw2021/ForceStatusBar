# 强制显示状态栏 - LSPosed/Xposed 模块

[![Build Status](https://github.com/YOUR_USERNAME/YOUR_REPO/workflows/Build%20LSPosed%20Module/badge.svg)](https://github.com/YOUR_USERNAME/YOUR_REPO/actions)
[![Release](https://img.shields.io/github/release/YOUR_USERNAME/YOUR_REPO.svg)](https://github.com/YOUR_USERNAME/YOUR_REPO/releases)

> **自动构建**: 本项目支持 GitHub Actions 自动构建，无需本地配置 Android 开发环境！

## 📖 文档索引

- [快速开始](QUICK_START.md) - 本地编译和使用指南
- [GitHub Actions 配置](GITHUB_ACTIONS.md) - 详细配置说明
- [GitHub Actions 快速开始](GITHUB_ACTIONS_QUICKSTART.md) - 5 分钟上手指南
- [项目结构](PROJECT_STRUCTURE.md) - 代码结构和实现原理

## 功能
在选定的应用中强制显示状态栏，防止游戏或全屏应用隐藏状态栏。

## 使用方法

### 1. 安装模块
- 将编译好的 APK 安装到手机
- 确保已安装 LSPosed 或 Xposed 框架

### 2. 激活模块
1. 打开 LSPosed 管理器
2. 找到"强制显示状态栏"模块
3. 启用模块
4. 在作用域中选择：
   - **系统框架**（可选，推荐选择以获得更好兼容性）
   - **目标应用**（你想要强制显示状态栏的游戏或应用）
5. 重启手机或重启目标应用

### 3. 验证效果
打开目标应用，状态栏应该始终显示在屏幕顶部。

## 技术原理
模块通过 Hook `Window` 类的以下方法来实现：
- `setFlags()` - 拦截 FLAG_FULLSCREEN 设置
- `addFlags()` - 阻止添加 FLAG_FULLSCREEN
- `clearFlags()` - 阻止清除 FLAG_FORCE_NOT_FULLSCREEN

## 编译方法

### 方法一：使用 GitHub Actions（推荐）

无需配置本地环境，直接在 GitHub 上自动构建：

1. **Fork 或创建仓库**
   - 将代码推送到 GitHub 仓库

2. **自动构建**
   - 每次推送代码会自动构建 Debug APK
   - 推送 tag（如 `v1.0.0`）会自动构建 Release APK 并创建 Release

3. **下载 APK**
   - 在 Actions 页面下载构建产物
   - 或在 Releases 页面下载发布版本

详细配置请参考 [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md)

### 方法二：使用 Android Studio
1. 打开项目
2. 同步 Gradle
3. Build -> Build Bundle(s) / APK(s) -> Build APK(s)

### 方法三：使用命令行
```bash
# 下载 Gradle Wrapper
./download-gradle-wrapper.sh

# 编译 Debug 版本
./gradlew assembleDebug

# 编译 Release 版本
./gradlew assembleRelease
```

编译后的 APK 位于：`app/build/outputs/apk/debug/app-debug.apk`

## 注意事项
- 需要 Root 权限和 LSPosed/Xposed 框架
- 部分游戏可能有额外的全屏检测机制，可能不完全生效
- 如果某些应用仍然全屏，尝试同时 Hook 系统框架

## 兼容性
- Android 8.0 (API 26) 及以上
- LSPosed 框架
- EdXposed 框架（可能支持）
- 原版 Xposed（Android 8.0 以下）

## 日志
模块日志会输出到 Xposed 日志中，可以在 LSPosed 管理器中查看。
