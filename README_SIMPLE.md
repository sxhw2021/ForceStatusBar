# 强制显示状态栏 v1.0.6

## 工作原理

### 方案：系统 UI 标志控制

```
游戏窗口
├── 状态栏（强制显示，半透明）
└── 游戏内容（延伸到状态栏下方，不被遮挡）
```

### 关键设置

1. **清除 FLAG_FULLSCREEN**
   - 阻止应用设置全屏
   - 强制系统显示状态栏

2. **设置 SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN**
   - 让游戏内容延伸到状态栏下方
   - 游戏使用完整屏幕高度
   - 状态栏半透明悬浮显示

3. **多次检查点设置**
   - Activity.onCreate
   - Activity.onResume
   - Activity.onWindowFocusChanged
   - Window.setFlags
   - View.setSystemUiVisibility

### 效果

- ✅ 状态栏始终显示
- ✅ 游戏内容不被遮挡（延伸到状态栏下方）
- ✅ 触摸坐标正常（不偏移）
- ✅ 适用于大多数游戏引擎

## 使用方法

1. **LSPosed 中启用模块**

2. **选择作用域**：只选择目标游戏应用（不需要系统框架）

3. **重启目标应用**（杀掉后台重新打开）

4. **享受状态栏**

## 注意事项

- 不需要选择系统框架
- 不需要重启手机
- 如果无效，尝试：
  1. 确保 LSPosed 作用域已勾选
  2. 完全关闭游戏（从最近任务划掉）
  3. 重新打开游戏

## 兼容性

- Android 8.0 - Android 14+
- LSPosed 框架
- 支持 Unity、Unreal、Cocos2dx 等主流引擎
