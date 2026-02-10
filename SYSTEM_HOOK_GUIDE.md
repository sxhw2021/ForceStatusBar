# 系统级 Hook 方案说明

## 核心原理

**欺骗策略**：Hook 系统框架，让游戏应用以为自己处于全屏模式（使用全屏坐标系），但系统实际上强制显示状态栏。

```
游戏应用 → 查询是否全屏 → Hook 返回 "是" → 游戏使用全屏坐标
                     ↓
系统框架 → 实际显示 → 强制显示状态栏（半透明悬浮）
```

## 使用方法

### 1. LSPosed 作用域配置（关键！）

必须同时选择以下三项：

- ✅ **系统框架** (android) - **必须选择！**
- ✅ **SystemUI** (com.android.systemui) - **必须选择！**
- ✅ **目标游戏应用** - 你想要强制显示状态栏的游戏

### 2. 配置步骤

1. 打开 **LSPosed 管理器**
2. 进入 **模块** 标签
3. 找到 **强制显示状态栏** 模块
4. 点击 **作用域**
5. 勾选：
   - **系统框架** (在最上面)
   - **SystemUI** 
   - 你的游戏应用（如 Candy Crush、PUBG 等）
6. **重启手机**（重要！）

### 3. 验证效果

- 打开游戏，状态栏会半透明悬浮在顶部
- 游戏内容延伸到状态栏下方（不遮挡）
- 触摸坐标正常（点击位置准确）

## 工作原理

### 系统框架 Hook (android)

1. **WindowState.isFullscreen()** - 欺骗系统认为窗口是全屏的
2. **DisplayPolicy** - 控制状态栏显示策略
3. **WindowManagerService** - 拦截全屏设置

### SystemUI Hook (com.android.systemui)

1. **StatusBar.hide()** - 阻止隐藏状态栏
2. **PhoneStatusBarView.setVisibility()** - 强制状态栏可见

### 目标应用 Hook

1. **Window.getAttributes()** - 移除全屏标志
2. **View.getSystemUiVisibility()** - 返回可见状态

## 注意事项

⚠️ **必须选择系统框架和 SystemUI**，否则无效！

⚠️ **必须重启手机**，系统框架 Hook 需要重启才能生效

⚠️ 某些深度定制的系统（如 MIUI、ColorOS）可能需要额外适配

## 故障排除

### 状态栏还是没有显示？

1. 检查 LSPosed 日志，确认系统框架是否 Hook 成功
2. 确认作用域中选择了 **系统框架** 和 **SystemUI**
3. 尝试重启手机

### 触摸还是有偏移？

1. 检查游戏是否真的被 Hook（查看 LSPosed 日志）
2. 某些游戏可能使用 Native 层，这种情况下可能需要额外的 Hook

### 系统不稳定？

1. 仅在目标游戏中启用模块（不要全局启用）
2. 如果出现问题，在 LSPosed 中禁用模块并重启

## 兼容性

- ✅ Android 8.0+ (API 26+)
- ✅ LSPosed 框架
- ✅ 大多数游戏引擎（Unity、Unreal、Cocos2dx）
- ⚠️ 部分深度定制系统可能需要额外适配
