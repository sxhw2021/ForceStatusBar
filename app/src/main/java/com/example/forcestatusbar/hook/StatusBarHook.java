package com.example.forcestatusbar.hook;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Field;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class StatusBarHook implements IXposedHookLoadPackage {
    
    private static final String TAG = "ForceStatusBar";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookActivityLifecycle(lpparam);
        hookWindowMethods(lpparam);
        startPeriodicCheck(lpparam);
        XposedBridge.log(TAG + ": 已初始化 - " + lpparam.packageName);
    }
    
    private void hookActivityLifecycle(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook onResume
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class.getName(),
                lpparam.classLoader,
                "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        final Activity activity = (Activity) param.thisObject;
                        // 立即执行
                        forceShowStatusBar(activity);
                        // 延迟再执行几次
                        postDelayedForceShow(activity, 100);
                        postDelayedForceShow(activity, 500);
                        postDelayedForceShow(activity, 1000);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook onResume 失败");
        }
    }
    
    private void postDelayedForceShow(final Activity activity, long delayMillis) {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activity != null && !activity.isFinishing()) {
                    forceShowStatusBar(activity);
                }
            }
        }, delayMillis);
    }
    
    private void hookWindowMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Window.setFlags
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
                            flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                            param.args[0] = flags;
                            XposedBridge.log(TAG + ": 拦截 FLAG_FULLSCREEN");
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook setFlags 失败");
        }
        
        // Hook Window.addFlags
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
                        if ((flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                            flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                            param.args[0] = flags;
                            XposedBridge.log(TAG + ": 拦截 addFlags FLAG_FULLSCREEN");
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook addFlags 失败");
        }
        
        // Hook View.setSystemUiVisibility
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
                        
                        // 保存原始值用于日志
                        int original = visibility;
                        
                        // 移除隐藏状态栏的标志
                        visibility &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
                        visibility &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                        visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE;
                        visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                        
                        // 添加显示状态栏的标志
                        visibility |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                        visibility |= View.SYSTEM_UI_FLAG_VISIBLE;
                        
                        if (visibility != original) {
                            param.args[0] = visibility;
                            XposedBridge.log(TAG + ": 修改 SystemUiVisibility 0x" + Integer.toHexString(original) + " -> 0x" + Integer.toHexString(visibility));
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook setSystemUiVisibility 失败");
        }
    }
    
    /**
     * 定时检查 - 确保状态栏始终显示
     */
    private void startPeriodicCheck(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class.getName(),
                lpparam.classLoader,
                "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        final Activity activity = (Activity) param.thisObject;
                        // 每500ms检查一次，持续5秒
                        for (int i = 1; i <= 10; i++) {
                            mainHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (activity != null && !activity.isFinishing()) {
                                        forceShowStatusBar(activity);
                                    }
                                }
                            }, i * 500);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 启动定时检查失败");
        }
    }
    
    /**
     * 核心方法：强制显示状态栏
     */
    private void forceShowStatusBar(Activity activity) {
        try {
            if (activity == null || activity.isFinishing()) return;
            
            Window window = activity.getWindow();
            if (window == null) return;
            
            View decorView = window.getDecorView();
            if (decorView == null) return;
            
            // 方法1：清除全屏标志
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            
            // 方法2：使用反射修改 WindowManager.LayoutParams
            try {
                WindowManager.LayoutParams attrs = window.getAttributes();
                if (attrs != null) {
                    attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                    attrs.flags |= WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
                    window.setAttributes(attrs);
                }
            } catch (Exception e) {
                // 忽略
            }
            
            // 方法3：Android 11+ 使用 WindowInsetsController
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
                
                if (window.getInsetsController() != null) {
                    window.getInsetsController().show(android.view.WindowInsets.Type.statusBars());
                }
                
                // 设置半透明状态栏
                window.setStatusBarColor(0x66000000);
            }
            
            // 方法4：设置 SystemUiVisibility
            int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                         View.SYSTEM_UI_FLAG_VISIBLE;
            decorView.setSystemUiVisibility(uiFlags);
            
            XposedBridge.log(TAG + ": 强制显示状态栏 - " + activity.getPackageName());
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 强制显示失败 - " + e.getMessage());
        }
    }
}
