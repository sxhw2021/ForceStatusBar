# 快速开始指南

## 1. 准备工作

### 必需工具
- Android Studio (推荐) 或 Gradle 命令行工具
- Android SDK (API 26+)
- JDK 8 或更高版本



## 2. 编译项目

### 使用 Android Studio
1. 打开 Android Studio
2. 选择 "Open an existing Android Studio project"
3. 选择 `ForceStatusBar` 文件夹
4. 等待 Gradle 同步完成
5. Build → Build Bundle(s) / APK(s) → Build APK(s)

### 使用命令行
```bash
# 进入项目目录
cd ForceStatusBar

# 使用 Gradle Wrapper 编译
./gradlew assembleDebug

# Windows 使用
gradlew.bat assembleDebug
```

编译后的 APK 位于：`app/build/outputs/apk/debug/app-debug.apk`

## 3. 安装到手机

### 使用 ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 手动安装
1. 将 APK 复制到手机
2. 使用文件管理器安装
3. 或使用 LSPosed 的模块安装功能

## 4. 配置 LSPosed

1. 打开 LSPosed 管理器
2. 进入「模块」标签
3. 找到「强制显示状态栏」模块
4. 启用模块开关
5. 点击「作用域」
6. 选择目标应用：
   - 勾选你想要强制显示状态栏的游戏/应用
   - （可选）勾选「系统框架」以获得更好兼容性
7. 返回并重启手机

## 5. 验证效果

1. 打开你选定的应用
2. 检查状态栏是否正常显示
3. 如果未生效，检查 LSPosed 日志

## 常见问题

### Q: 编译时提示找不到 Xposed API
**A**: 
1. 确保 `settings.gradle` 中配置了 Xposed Maven 仓库：`maven { url 'https://api.xposed.info/' }`
2. 点击 Android Studio 中的 "Sync Project with Gradle Files"
3. 等待 Gradle 自动下载依赖

### Q: 模块已启用但状态栏仍被隐藏
**A**: 
1. 确保已正确选择目标应用的作用域
2. 尝试同时勾选「系统框架」
3. 重启手机
4. 检查 LSPosed 日志是否有错误

### Q: 某些游戏无效
**A**: 
- 部分游戏可能使用 Native 层控制全屏，这类应用可能无法被 Hook
- 尝试查找游戏的特定设置选项

### Q: 如何查看日志？
**A**:
```bash
# 使用 ADB 查看 LSPosed 日志
adb shell cat /data/adb/lspd/log/modules.log

# 或查看所有 Xposed 日志
adb logcat -s Xposed LSPosed ForceStatusBar
```

## 技术说明

### Hook 点
模块 Hook 了以下方法：
1. `Window.setFlags()` - 主要入口
2. `Window.addFlags()` - 防止动态添加
3. `Window.clearFlags()` - 防止清除强制非全屏标志
4. `Window.setAttributes()` - 处理直接设置属性
5. `View.setSystemUiVisibility()` - Android 10 及以下
6. `WindowInsetsController.hide/show()` - Android 11+

### 兼容性
- Android 8.0+ (API 26+)
- LSPosed 框架（推荐）
- EdXposed（可能支持）
- Android 11+ 需要额外的 WindowInsetsController Hook

## 卸载

1. 在 LSPosed 中禁用模块
2. 正常卸载应用即可
3. 重启手机以完全清除 Hook
