# ForceStatusBar

LSPosed/Xposed 模块 - 强制显示状态栏

## 目录结构
```
ForceStatusBar/
├── app/
│   ├── build.gradle              # 模块构建配置
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml
│   │       ├── assets/
│   │       │   └── xposed_init   # Xposed 入口
│   │       ├── java/
│   │       │   └── com/
│   │       │       └── example/
│   │       │           └── forcestatusbar/
│   │       │               ├── hook/
│   │       │               │   └── StatusBarHook.java  # 核心 Hook
│   │       │               └── ui/
│   │       │                   └── MainActivity.java   # 主界面
│   │       └── res/
│   │           ├── layout/
│   │           │   └── activity_main.xml
│   │           └── values/
│   │               ├── strings.xml
│   │               └── colors.xml
│   └── libs/                     # Xposed API JAR
├── build.gradle                  # 项目构建配置
├── settings.gradle
├── gradle.properties
└── README.md
```

## 快速开始

### 1. 添加 Xposed API
在编译前，需要下载 Xposed API JAR 文件：

```bash
# 创建 libs 目录
mkdir -p app/libs

# 下载 Xposed API（需要 wget 或手动下载）
wget https://repo1.maven.org/maven2/de/robv/android/xposed/api/82/api-82.jar -O app/libs/api-82.jar
```

或者手动下载：
- 访问 https://repo1.maven.org/maven2/de/robv/android/xposed/api/82/
- 下载 `api-82.jar` 和 `api-82-sources.jar`
- 放入 `app/libs/` 目录

### 2. 编译
使用 Android Studio 或 Gradle 命令行编译：

```bash
# 使用 Gradle Wrapper（推荐）
./gradlew assembleDebug

# 或在 Windows
gradlew.bat assembleDebug
```

### 3. 安装
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 核心代码说明

### StatusBarHook.java

主要 Hook 了三个方法：

1. **setFlags()** - 拦截设置全屏标志
   - 检测 `FLAG_FULLSCREEN` 和 `FLAG_LAYOUT_NO_LIMITS`
   - 从 flags 中移除这些标志

2. **addFlags()** - 阻止添加全屏标志
   - 当应用尝试添加 `FLAG_FULLSCREEN` 时移除它

3. **clearFlags()** - 阻止清除强制非全屏标志
   - 防止应用清除 `FLAG_FORCE_NOT_FULLSCREEN`

## 已知问题

1. 某些游戏可能使用原生层（Native）设置全屏，这种情况下模块可能无效
2. 部分应用可能动态检测状态栏高度变化并重新设置全屏
3. 需要使用最新的 LSPosed 版本以获得最佳兼容性

## 调试

查看 Xposed 日志：
```bash
adb shell cat /data/data/de.robv.android.xposed.installer/log/error.log
```

或在 LSPosed 管理器中查看日志。

## 许可证

MIT License
