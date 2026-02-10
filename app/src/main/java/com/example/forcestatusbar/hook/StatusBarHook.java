package com.example.forcestatusbar.hook;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class StatusBarHook implements IXposedHookLoadPackage {
    
    private static final String TAG = "ForceStatusBar";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // 记录已hook的activity，避免重复
    private static final WeakHashMap<Activity, Boolean> hookedActivities = new WeakHashMap<>();
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookActivityLifecycle(lpparam);
        hookWindowMethods(lpparam);
        hookViewCreation(lpparam);
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
                        if (hookedActivities.containsKey(activity)) {
                            return;
                        }
                        hookedActivities.put(activity, true);
                        
                        // 启动持续守护线程
                        startGuardian(activity);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook onResume 失败");
        }
        
        // Hook onPause - 停止守护
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class.getName(),
                lpparam.classLoader,
                "onPause",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        hookedActivities.remove(activity);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook onPause 失败");
        }
    }
    
    /**
     * 持续守护线程 - 每200ms强制显示一次状态栏
     */
    private void startGuardian(final Activity activity) {
        final Runnable guardianTask = new Runnable() {
            @Override
            public void run() {
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                
                // 强制显示状态栏
                forceShowStatusBar(activity);
                
                // 继续下一次
                mainHandler.postDelayed(this, 200);
            }
        };
        
        // 立即开始
        mainHandler.post(guardianTask);
        XposedBridge.log(TAG + ": 启动守护线程 - " + activity.getPackageName());
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
                            XposedBridge.log(TAG + ": 拦截 setFlags FLAG_FULLSCREEN");
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
                        int original = visibility;
                        
                        // 只移除隐藏状态栏的标志
                        visibility &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
                        visibility &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                        visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE;
                        visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                        
                        // 确保基本标志
                        visibility |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                        visibility |= View.SYSTEM_UI_FLAG_VISIBLE;
                        
                        if (visibility != original) {
                            param.args[0] = visibility;
                            XposedBridge.log(TAG + ": 拦截 SystemUiVisibility 0x" + Integer.toHexString(original));
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook setSystemUiVisibility 失败");
        }
    }
    
    /**
     * Hook SurfaceView/TextureView 创建 - 游戏引擎常用
     */
    private void hookViewCreation(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook SurfaceView 创建
        try {
            XposedHelpers.findAndHookMethod(
                SurfaceView.class.getName(),
                lpparam.classLoader,
                "onAttachedToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        Activity activity = getActivityFromView(view);
                        if (activity != null) {
                            forceShowStatusBar(activity);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook SurfaceView 失败");
        }
        
        // Hook TextureView 创建
        try {
            XposedHelpers.findAndHookMethod(
                TextureView.class.getName(),
                lpparam.classLoader,
                "onAttachedToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        Activity activity = getActivityFromView(view);
                        if (activity != null) {
                            forceShowStatusBar(activity);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook TextureView 失败");
        }
    }
    
    private Activity getActivityFromView(View view) {
        try {
            Context context = view.getContext();
            if (context instanceof Activity) {
                return (Activity) context;
            }
        } catch (Exception e) {
            // 忽略
        }
        return null;
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
            
            // 方法2：修改 LayoutParams
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
            
            // 方法3：Android 11+ 使用新API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
                if (window.getInsetsController() != null) {
                    window.getInsetsController().show(android.view.WindowInsets.Type.statusBars());
                }
                window.setStatusBarColor(0x66000000);
            }
            
            // 方法4：设置 SystemUiVisibility
            int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                         View.SYSTEM_UI_FLAG_VISIBLE;
            decorView.setSystemUiVisibility(uiFlags);
            
        } catch (Exception e) {
            // 忽略错误
        }
    }
}
