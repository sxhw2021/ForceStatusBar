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

public class StatusBarHook implements IXposedHookLoadPackage {
    
    private static final String TAG = "ForceStatusBar";
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookActivityLifecycle(lpparam);
        hookWindowMethods(lpparam);
        XposedBridge.log(TAG + ": 已初始化 - " + lpparam.packageName);
    }
    
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
                        forceShowStatusBar(activity);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook onCreate 失败");
        }
        
        // Hook onResume - 主要执行点
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class.getName(),
                lpparam.classLoader,
                "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        final Activity activity = (Activity) param.thisObject;
                        // 延迟执行确保窗口准备好
                        activity.getWindow().getDecorView().post(new Runnable() {
                            @Override
                            public void run() {
                                forceShowStatusBar(activity);
                            }
                        });
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook onResume 失败");
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
                            Activity activity = (Activity) param.thisObject;
                            forceShowStatusBar(activity);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook onWindowFocusChanged 失败");
        }
    }
    
    private void hookWindowMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Window.setFlags - 阻止设置全屏
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
                        
                        if ((mask & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                            if ((flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                                flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                                param.args[0] = flags;
                                XposedBridge.log(TAG + ": 拦截 FLAG_FULLSCREEN");
                            }
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook setFlags 失败");
        }
    }
    
    /**
     * 核心方法：强制显示状态栏
     */
    private void forceShowStatusBar(Activity activity) {
        try {
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            
            // 清除全屏标志
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (包括你的 Android 15)
                window.setDecorFitsSystemWindows(false);
                
                // 显示状态栏
                if (window.getInsetsController() != null) {
                    window.getInsetsController().show(android.view.WindowInsets.Type.statusBars());
                }
                
                // 设置半透明黑色状态栏
                window.setStatusBarColor(0x66000000);
                
            } else {
                // Android 10 及以下
                int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                             View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                             View.SYSTEM_UI_FLAG_VISIBLE;
                decorView.setSystemUiVisibility(uiFlags);
            }
            
            XposedBridge.log(TAG + ": 状态栏已显示 - " + activity.getPackageName());
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 显示状态栏失败 - " + e.getMessage());
        }
    }
}
