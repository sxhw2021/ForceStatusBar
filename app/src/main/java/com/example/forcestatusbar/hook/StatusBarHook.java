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
        hookWindowMethods(lpparam);
        hookDecorView(lpparam);
        hookActivityLifecycle(lpparam);
        
        // Android 11+ 使用 WindowInsetsController
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hookWindowInsetsControllerImpl(lpparam);
        }
        
        XposedBridge.log(TAG + ": 已初始化 - " + lpparam.packageName);
    }
    
    private void hookWindowMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Window 类的 setFlags 方法
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
                        
                        boolean modified = false;
                        
                        // 检查是否设置了 FLAG_FULLSCREEN
                        if ((mask & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                            flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                            modified = true;
                        }
                        
                        // 移除 FLAG_LAYOUT_NO_LIMITS（某些游戏也会使用这个）
                        if ((mask & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0) {
                            flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                            modified = true;
                        }
                        
                        if (modified) {
                            param.args[0] = flags;
                            XposedBridge.log(TAG + ": 拦截 setFlags - " + lpparam.packageName);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook setFlags 失败 - " + e.getMessage());
        }
        
        // Hook addFlags 方法
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
                        
                        // 如果添加 FLAG_FULLSCREEN，则阻止
                        if ((flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                            flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                            param.args[0] = flags;
                            XposedBridge.log(TAG + ": 拦截 addFlags FLAG_FULLSCREEN - " + lpparam.packageName);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook addFlags 失败 - " + e.getMessage());
        }
        
        // Hook clearFlags 方法
        try {
            XposedHelpers.findAndHookMethod(
                Window.class.getName(),
                lpparam.classLoader,
                "clearFlags",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        int flags = (int) param.args[0];
                        
                        // 如果尝试清除 FLAG_FORCE_NOT_FULLSCREEN，阻止它
                        if ((flags & WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN) != 0) {
                            flags &= ~WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
                            param.args[0] = flags;
                            XposedBridge.log(TAG + ": 阻止清除 FLAG_FORCE_NOT_FULLSCREEN - " + lpparam.packageName);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook clearFlags 失败 - " + e.getMessage());
        }
        
        // Hook setAttributes 方法
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
                            boolean modified = false;
                            
                            // 移除全屏标志
                            if ((attrs.flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                                attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                                modified = true;
                            }
                            
                            if ((attrs.flags & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0) {
                                attrs.flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                                modified = true;
                            }
                            
                            // 强制添加 FLAG_FORCE_NOT_FULLSCREEN
                            if ((attrs.flags & WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN) == 0) {
                                attrs.flags |= WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
                                modified = true;
                            }
                            
                            if (modified) {
                                XposedBridge.log(TAG + ": 拦截 setAttributes - " + lpparam.packageName);
                            }
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook setAttributes 失败 - " + e.getMessage());
        }
    }
    
    private void hookDecorView(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook DecorView 的 setSystemUiVisibility (Android 10 及以下)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
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
                            
                            // 移除全屏相关的 flag
                            int oldVisibility = visibility;
                            visibility &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
                            visibility &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                            visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE;
                            visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                            
                            // 确保状态栏可见
                            visibility |= View.SYSTEM_UI_FLAG_VISIBLE;
                            
                            if (visibility != oldVisibility) {
                                param.args[0] = visibility;
                                XposedBridge.log(TAG + ": 拦截 setSystemUiVisibility - " + lpparam.packageName);
                            }
                        }
                    }
                );
            } catch (Exception e) {
                XposedBridge.log(TAG + ": Hook setSystemUiVisibility 失败 - " + e.getMessage());
            }
        }
    }
    
    private void hookActivityLifecycle(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Activity 的 onResume，确保状态栏显示并避免遮挡
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class.getName(),
                lpparam.classLoader,
                "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        Window window = activity.getWindow();
                        
                        // 清除全屏标志
                        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                        
                        // 强制显示状态栏
                        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                        
                        // 避免状态栏遮挡应用内容
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Android 11+：使用 WindowInsetsController
                            try {
                                window.setDecorFitsSystemWindows(true);
                            } catch (Exception e) {
                                // 忽略错误
                            }
                        } else {
                            // Android 10 及以下：使用 SYSTEM_UI_FLAG 和 fitsSystemWindows
                            View decorView = window.getDecorView();
                            int uiFlags = View.SYSTEM_UI_FLAG_VISIBLE | 
                                         View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                            decorView.setSystemUiVisibility(uiFlags);
                            
                            // 尝试设置 fitsSystemWindows
                            try {
                                View contentView = window.findViewById(android.R.id.content);
                                if (contentView != null) {
                                    contentView.setFitsSystemWindows(true);
                                }
                            } catch (Exception ignored) {}
                        }
                        
                        XposedBridge.log(TAG + ": Activity.onResume 强制显示状态栏并避免遮挡 - " + lpparam.packageName);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook Activity.onResume 失败 - " + e.getMessage());
        }
        
        // Hook Activity 的 onCreate，在创建时就设置
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
                        
                        // 强制添加 FLAG_FORCE_NOT_FULLSCREEN
                        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                        
                        XposedBridge.log(TAG + ": Activity.onCreate 设置状态栏标志 - " + lpparam.packageName);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook Activity.onCreate 失败 - " + e.getMessage());
        }
    }
    
    private void hookWindowInsetsControllerImpl(XC_LoadPackage.LoadPackageParam lpparam) {
        // Android 11+ 尝试 Hook WindowInsetsController 的具体实现类
        String[] implClasses = {
            "android.view.WindowInsetsControllerImpl",
            "android.view.InsetsController",
            "android.view.WindowInsetsController$Impl"
        };
        
        for (String className : implClasses) {
            try {
                Class<?> clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (clazz != null) {
                    XposedBridge.hookAllMethods(clazz, "hide", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            int types = (int) param.args[0];
                            
                            // 尝试获取 statusBars 类型
                            int statusBars = 0;
                            try {
                                Class<?> typeClass = XposedHelpers.findClass(
                                    "android.view.WindowInsets$Type", 
                                    lpparam.classLoader
                                );
                                statusBars = (int) XposedHelpers.callStaticMethod(typeClass, "statusBars");
                            } catch (Exception ignored) {}
                            
                            // 如果尝试隐藏状态栏，阻止它
                            if (statusBars != 0 && (types & statusBars) != 0) {
                                types &= ~statusBars;
                                param.args[0] = types;
                                XposedBridge.log(TAG + ": 拦截 WindowInsetsControllerImpl.hide(状态栏) - " + lpparam.packageName);
                            }
                        }
                    });
                    
                    XposedBridge.log(TAG + ": 成功 Hook WindowInsetsController 实现类 - " + className);
                    break; // 成功一个就退出
                }
            } catch (Exception e) {
                // 尝试下一个类
            }
        }
    }
}
