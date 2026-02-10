package com.example.forcestatusbar.hook;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 强制显示状态栏模块 - 简单可靠方案
 * 
 * 核心思路：
 * 1. 清除 FLAG_FULLSCREEN - 让系统显示状态栏
 * 2. 设置 SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN - 让内容延伸到状态栏下方（不遮挡）
 * 3. 不做任何视图位置调整 - 避免触摸偏移
 */
public class StatusBarHook implements IXposedHookLoadPackage {
    
    private static final String TAG = "ForceStatusBar";
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookWindowFlags(lpparam);
        hookSystemUiVisibility(lpparam);
        hookActivityLifecycle(lpparam);
        
        XposedBridge.log(TAG + ": 已初始化 - " + lpparam.packageName);
    }
    
    /**
     * Hook Window 的标志设置
     */
    private void hookWindowFlags(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook setFlags
        try {
            XposedHelpers.findAndHookMethod(
                Window.class.getName(),
                lpparam.classLoader,
                "setFlags",
                int.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        int flags = (int) param.args[0];
                        int mask = (int) param.args[1];
                        
                        // 如果尝试设置 FLAG_FULLSCREEN，移除它
                        if ((mask & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                            if ((flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                                flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                                param.args[0] = flags;
                                XposedBridge.log(TAG + ": 拦截 setFlags FLAG_FULLSCREEN");
                            }
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook setFlags 失败 - " + e.getMessage());
        }
        
        // Hook addFlags
        try {
            XposedHelpers.findAndHookMethod(
                Window.class.getName(),
                lpparam.classLoader,
                "addFlags",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        int flags = (int) param.args[0];
                        
                        // 阻止添加 FLAG_FULLSCREEN
                        if ((flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                            flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                            param.args[0] = flags;
                            XposedBridge.log(TAG + ": 拦截 addFlags FLAG_FULLSCREEN");
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook addFlags 失败 - " + e.getMessage());
        }
        
        // Hook setAttributes
        try {
            XposedHelpers.findAndHookMethod(
                Window.class.getName(),
                lpparam.classLoader,
                "setAttributes",
                WindowManager.LayoutParams.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        WindowManager.LayoutParams attrs = (WindowManager.LayoutParams) param.args[0];
                        if (attrs != null) {
                            // 移除全屏标志
                            attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                            // 强制非全屏
                            attrs.flags |= WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
                            XposedBridge.log(TAG + ": 拦截 setAttributes");
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook setAttributes 失败 - " + e.getMessage());
        }
    }
    
    /**
     * Hook System UI 可见性
     */
    private void hookSystemUiVisibility(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                View.class.getName(),
                lpparam.classLoader,
                "setSystemUiVisibility",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        int visibility = (int) param.args[0];
                        int oldVisibility = visibility;
                        
                        // 移除隐藏状态栏的标志
                        visibility &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
                        visibility &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                        visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE;
                        visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                        
                        // 添加布局全屏标志（内容延伸到状态栏下方）
                        visibility |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                        visibility |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                        
                        if (visibility != oldVisibility) {
                            param.args[0] = visibility;
                            XposedBridge.log(TAG + ": 调整 SystemUiVisibility - 0x" + Integer.toHexString(oldVisibility) + " -> 0x" + Integer.toHexString(visibility));
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook setSystemUiVisibility 失败 - " + e.getMessage());
        }
    }
    
    /**
     * Hook Activity 生命周期
     */
    private void hookActivityLifecycle(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook onCreate
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class.getName(),
                lpparam.classLoader,
                "onCreate",
                android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        Window window = activity.getWindow();
                        
                        // 清除全屏标志
                        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                        
                        // 强制非全屏
                        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                        
                        XposedBridge.log(TAG + ": Activity.onCreate - " + activity.getPackageName());
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook onCreate 失败 - " + e.getMessage());
        }
        
        // Hook onResume - 关键：在这里设置 System UI
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class.getName(),
                lpparam.classLoader,
                "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        final Activity activity = (Activity) param.thisObject;
                        final Window window = activity.getWindow();
                        final View decorView = window.getDecorView();
                        
                        // 延迟执行，确保 Activity 完全恢复
                        decorView.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    // 清除全屏标志
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                                    
                                    // 强制非全屏
                                    window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                                    
                                    // 关键：设置 System UI Flags
                                    // LAYOUT_FULLSCREEN - 让内容延伸到状态栏下方
                                    // LAYOUT_STABLE - 保持布局稳定
                                    int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                                 View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                                 View.SYSTEM_UI_FLAG_VISIBLE;
                                    
                                    decorView.setSystemUiVisibility(uiFlags);
                                    
                                    // Android 11+ 使用新的 API
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        // 让内容延伸到状态栏下方
                                        window.setDecorFitsSystemWindows(false);
                                        
                                        // 显示状态栏
                                        if (window.getInsetsController() != null) {
                                            window.getInsetsController().show(android.view.WindowInsets.Type.statusBars());
                                        }
                                    }
                                    
                                    XposedBridge.log(TAG + ": 应用状态栏设置 - " + activity.getPackageName());
                                } catch (Exception e) {
                                    XposedBridge.log(TAG + ": 设置状态栏失败 - " + e.getMessage());
                                }
                            }
                        });
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook onResume 失败 - " + e.getMessage());
        }
        
        // Hook onWindowFocusChanged
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class.getName(),
                lpparam.classLoader,
                "onWindowFocusChanged",
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        boolean hasFocus = (boolean) param.args[0];
                        if (hasFocus) {
                            final Activity activity = (Activity) param.thisObject;
                            final Window window = activity.getWindow();
                            
                            // 再次确保标志正确
                            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                            window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook onWindowFocusChanged 失败 - " + e.getMessage());
        }
    }
}
