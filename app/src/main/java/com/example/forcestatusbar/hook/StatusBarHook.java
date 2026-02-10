package com.example.forcestatusbar.hook;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 强制显示状态栏模块 - 系统级欺骗方案
 * 
 * 核心思路：欺骗应用让它以为自己处于全屏模式（这样游戏使用全屏坐标系），
 * 但在系统层面强制显示状态栏。
 */
public class StatusBarHook implements IXposedHookLoadPackage {
    
    private static final String TAG = "ForceStatusBar";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;
        
        // 1. Hook 系统框架 - 核心逻辑
        if (packageName.equals("android")) {
            hookSystemFramework(lpparam);
            return;
        }
        
        // 2. Hook SystemUI - 强制显示状态栏
        if (packageName.equals(SYSTEMUI_PACKAGE)) {
            hookSystemUI(lpparam);
            return;
        }
        
        // 3. Hook 目标应用 - 欺骗应用让它以为全屏
        hookTargetApp(lpparam);
    }
    
    /**
     * Hook 系统框架 - 欺骗窗口管理器
     */
    private void hookSystemFramework(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook WindowManagerService 的 setAppFullscreen
            Class<?> windowManagerServiceClass = XposedHelpers.findClass(
                "com.android.server.wm.WindowManagerService",
                lpparam.classLoader
            );
            
            // Hook 设置全屏状态的方法
            XposedHelpers.findAndHookMethod(
                windowManagerServiceClass,
                "setAppFullscreen",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // 阻止系统设置全屏状态
                        // 应用会收到全屏回调，但系统不会真正隐藏状态栏
                        XposedBridge.log(TAG + ": 拦截系统设置全屏");
                    }
                }
            );
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook WindowManagerService 失败 - " + e.getMessage());
        }
        
        try {
            // Hook WindowState 的 isFullscreen 方法
            // 让系统认为窗口是全屏的（这样游戏使用全屏布局）
            Class<?> windowStateClass = XposedHelpers.findClass(
                "com.android.server.wm.WindowState",
                lpparam.classLoader
            );
            
            XposedHelpers.findAndHookMethod(
                windowStateClass,
                "isFullscreen",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        // 欺骗系统：返回 true 表示是全屏
                        // 这样游戏认为自己全屏，使用全屏坐标系
                        return true;
                    }
                }
            );
            
            XposedBridge.log(TAG + ": Hook WindowState.isFullscreen 成功");
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook WindowState 失败 - " + e.getMessage());
        }
        
        try {
            // Hook DisplayPolicy - 控制状态栏显示策略
            Class<?> displayPolicyClass = XposedHelpers.findClass(
                "com.android.server.wm.DisplayPolicy",
                lpparam.classLoader
            );
            
            // Hook 更新系统 UI 的方法
            XposedHelpers.findAndHookMethod(
                displayPolicyClass,
                "updateSystemUiVisibilityLw",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // 强制显示状态栏
                        Object mStatusBar = XposedHelpers.getObjectField(param.thisObject, "mStatusBar");
                        if (mStatusBar != null) {
                            XposedHelpers.callMethod(mStatusBar, "show", new Object[]{});
                        }
                    }
                }
            );
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook DisplayPolicy 失败 - " + e.getMessage());
        }
    }
    
    /**
     * Hook SystemUI - 强制状态栏显示
     */
    private void hookSystemUI(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook StatusBar 的 hide 方法
            Class<?> statusBarClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.StatusBar",
                lpparam.classLoader
            );
            
            XposedHelpers.findAndHookMethod(
                statusBarClass,
                "hide",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        // 直接返回，不执行隐藏
                        XposedBridge.log(TAG + ": 阻止隐藏状态栏");
                        return null;
                    }
                }
            );
            
            // Hook makeExpandedInvisible 方法
            XposedHelpers.findAndHookMethod(
                statusBarClass,
                "makeExpandedInvisible",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // 确保状态栏保持显示
                        Object statusBar = param.thisObject;
                        XposedHelpers.callMethod(statusBar, "show", new Object[]{});
                    }
                }
            );
            
            XposedBridge.log(TAG + ": Hook SystemUI 成功");
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook SystemUI 失败 - " + e.getMessage());
        }
        
        try {
            // Hook PhoneStatusBarView
            Class<?> phoneStatusBarViewClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.PhoneStatusBarView",
                lpparam.classLoader
            );
            
            XposedHelpers.findAndHookMethod(
                phoneStatusBarViewClass,
                "setVisibility",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        int visibility = (int) param.args[0];
                        if (visibility == View.GONE || visibility == View.INVISIBLE) {
                            // 强制改为 VISIBLE
                            param.args[0] = View.VISIBLE;
                            XposedBridge.log(TAG + ": 强制状态栏可见");
                        }
                    }
                }
            );
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook PhoneStatusBarView 失败 - " + e.getMessage());
        }
    }
    
    /**
     * Hook 目标应用 - 欺骗应用让它以为自己全屏
     */
    private void hookTargetApp(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Activity 的 isInMultiWindowMode（某些游戏用这个判断）
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class.getName(),
                lpparam.classLoader,
                "isInMultiWindowMode",
                new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        // 返回 false，让游戏认为不在多窗口模式
                        return false;
                    }
                }
            );
        } catch (Exception e) {
            // 忽略
        }
        
        // Hook WindowManager 的相关方法
        try {
            XposedHelpers.findAndHookMethod(
                Window.class.getName(),
                lpparam.classLoader,
                "getAttributes",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        WindowManager.LayoutParams attrs = (WindowManager.LayoutParams) param.getResult();
                        if (attrs != null) {
                            // 移除全屏标志，这样系统会显示状态栏
                            attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                            attrs.flags |= WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook Window.getAttributes 失败 - " + e.getMessage());
        }
        
        // Hook 系统 UI 可见性查询
        try {
            XposedHelpers.findAndHookMethod(
                View.class.getName(),
                lpparam.classLoader,
                "getSystemUiVisibility",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        int visibility = (int) param.getResult();
                        // 移除全屏标志
                        visibility &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
                        visibility &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                        visibility |= View.SYSTEM_UI_FLAG_VISIBLE;
                        param.setResult(visibility);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook View.getSystemUiVisibility 失败 - " + e.getMessage());
        }
        
        XposedBridge.log(TAG + ": 已 Hook 目标应用 - " + lpparam.packageName);
    }
}
