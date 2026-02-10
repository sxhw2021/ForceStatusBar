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

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class StatusBarHook implements IXposedHookLoadPackage {

    private static final String TAG = "ForceStatusBar";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final long GUARD_INTERVAL_MS = 500;
    private static final long FULLSCREEN_DETECTION_DELAY_MS = 100;

    private static final WeakHashMap<Activity, GuardInfo> guardedActivities = new WeakHashMap<>();

    private static class GuardInfo {
        Runnable guardTask;
        boolean isFullscreen;
        long lastCheckTime;

        GuardInfo(Runnable task) {
            this.guardTask = task;
            this.isFullscreen = false;
            this.lastCheckTime = 0;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookWindowFlags(lpparam);
        hookViewCreation(lpparam);
        XposedBridge.log(TAG + ": 已初始化 - " + lpparam.packageName);
    }
    
    private void hookWindowFlags(XC_LoadPackage.LoadPackageParam lpparam) {
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

                        final Activity activity = getActivityFromParam(param);
                        if (activity != null) {
                            mainHandler.postDelayed(() -> startGuarding(activity), FULLSCREEN_DETECTION_DELAY_MS);
                        }
                    }
                }
            }
        );

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

                        final Activity activity = getActivityFromParam(param);
                        if (activity != null) {
                            mainHandler.postDelayed(() -> startGuarding(activity), FULLSCREEN_DETECTION_DELAY_MS);
                        }
                    }
                }
            }
        );
    }

    private Activity getActivityFromParam(XC_MethodHook.MethodHookParam param) {
        try {
            Window window = (Window) param.thisObject;
            return (Activity) window.getContext();
        } catch (Exception e) {
            return null;
        }
    }

    private void startGuarding(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        synchronized (guardedActivities) {
            if (guardedActivities.containsKey(activity)) {
                GuardInfo existing = guardedActivities.get(activity);
                if (existing != null) {
                    existing.isFullscreen = true;
                    existing.lastCheckTime = System.currentTimeMillis();
                }
                return;
            }

            Runnable guardTask = new Runnable() {
                @Override
                public void run() {
                    if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                        stopGuarding(activity);
                        return;
                    }

                    GuardInfo info = guardedActivities.get(activity);
                    if (info == null || !info.isFullscreen) {
                        return;
                    }

                    forceShowStatusBar(activity);
                    mainHandler.postDelayed(this, GUARD_INTERVAL_MS);
                }
            };

            GuardInfo guardInfo = new GuardInfo(guardTask);
            guardedActivities.put(activity, guardInfo);
            mainHandler.post(guardTask);
            XposedBridge.log(TAG + ": 启动守护 - " + activity.getClass().getSimpleName());
        }
    }

    private void stopGuarding(Activity activity) {
        synchronized (guardedActivities) {
            GuardInfo info = guardedActivities.remove(activity);
            if (info != null && info.guardTask != null) {
                mainHandler.removeCallbacks(info.guardTask);
            }
        }
    }
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
    
    private void hookViewCreation(XC_LoadPackage.LoadPackageParam lpparam) {
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
                            mainHandler.postDelayed(() -> startGuarding(activity), FULLSCREEN_DETECTION_DELAY_MS);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook SurfaceView 失败");
        }

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
                            mainHandler.postDelayed(() -> startGuarding(activity), FULLSCREEN_DETECTION_DELAY_MS);
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
            if (view.getContext() instanceof Activity) {
                return (Activity) view.getContext();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    private void forceShowStatusBar(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            stopGuarding(activity);
            return;
        }

        try {
            Window window = activity.getWindow();
            if (window == null) return;

            View decorView = window.getDecorView();
            if (decorView == null) return;

            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
                if (window.getInsetsController() != null) {
                    window.getInsetsController().show(android.view.WindowInsets.Type.statusBars());
                }
                window.setStatusBarColor(0x40000000);
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                             View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                             View.SYSTEM_UI_FLAG_VISIBLE;
                decorView.setSystemUiVisibility(uiFlags);
            }

            synchronized (guardedActivities) {
                GuardInfo info = guardedActivities.get(activity);
                if (info != null) {
                    info.lastCheckTime = System.currentTimeMillis();
                }
            }
        } catch (Exception ignored) {
        }
    }
}
