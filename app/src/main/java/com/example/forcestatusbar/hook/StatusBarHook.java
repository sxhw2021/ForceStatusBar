package com.example.forcestatusbar.hook;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
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

    private static final long MAX_GUARD_TIME = 5000;

    private static final java.util.Map<Activity, GuardTask> guardedActivities = new java.util.WeakHashMap<>();

    private static class GuardTask implements Runnable {
        private final Activity activity;
        private final long startTime;
        private long lastRun;
        private volatile boolean stopped = false;

        GuardTask(Activity activity) {
            this.activity = activity;
            this.startTime = System.currentTimeMillis();
            this.lastRun = 0;
        }

        @Override
        public void run() {
            if (stopped || activity == null || activity.isFinishing() || activity.isDestroyed()) {
                stopGuarding(activity);
                return;
            }

            if (System.currentTimeMillis() - startTime > MAX_GUARD_TIME) {
                stopGuarding(activity);
                return;
            }

            forceShowStatusBar(activity);

            long interval = 100;
            if (System.currentTimeMillis() - lastRun < 200) {
                interval = 200;
            }
            lastRun = System.currentTimeMillis();

            if (!stopped) {
                mainHandler.postDelayed(this, interval);
            }
        }

        public void stop() {
            stopped = true;
            mainHandler.removeCallbacks(this);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log(TAG + ": 开始 Hook - " + lpparam.packageName);

        hookSystemActivity(lpparam);
        hookSystemWindow(lpparam);
        hookSurfaceView(lpparam);

        XposedBridge.log(TAG + ": 已初始化 - " + lpparam.packageName);
    }

    private void hookSystemActivity(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    startGuard(activity);
                }
            });
            XposedBridge.log(TAG + ": Hook Activity.onResume 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook Activity.onResume 失败 - " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onWindowFocusChanged", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if ((boolean) param.args[0]) {
                        Activity activity = (Activity) param.thisObject;
                        startGuard(activity);
                    }
                }
            });
            XposedBridge.log(TAG + ": Hook Activity.onWindowFocusChanged 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook Activity.onWindowFocusChanged 失败 - " + t.getMessage());
        }
    }

    private void hookSystemWindow(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(Window.class, "setFlags", int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int flags = (int) param.args[0];
                    int mask = (int) param.args[1];

                    if ((mask & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                        flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                        param.args[0] = flags;
                        XposedBridge.log(TAG + ": 拦截 setFlags FLAG_FULLSCREEN");
                        forceShowStatusBarFromWindow(param);
                    }
                }
            });
            XposedBridge.log(TAG + ": Hook Window.setFlags 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook Window.setFlags 失败 - " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(Window.class, "addFlags", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int flags = (int) param.args[0];
                    if ((flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                        flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                        param.args[0] = flags;
                        XposedBridge.log(TAG + ": 拦截 addFlags FLAG_FULLSCREEN");
                        forceShowStatusBarFromWindow(param);
                    }
                }
            });
            XposedBridge.log(TAG + ": Hook Window.addFlags 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook Window.addFlags 失败 - " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(View.class, "setSystemUiVisibility", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int visibility = (int) param.args[0];
                    int original = visibility;

                    if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0) {
                        visibility &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
                    }
                    if ((visibility & View.SYSTEM_UI_FLAG_IMMERSIVE) != 0) {
                        visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE;
                    }
                    if ((visibility & View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0) {
                        visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                    }

                    visibility |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                    visibility |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                    visibility |= View.SYSTEM_UI_FLAG_VISIBLE;

                    if (visibility != original) {
                        param.args[0] = visibility;
                        XposedBridge.log(TAG + ": 拦截 SystemUiVisibility 0x" + Integer.toHexString(original));
                    }
                }
            });
            XposedBridge.log(TAG + ": Hook View.setSystemUiVisibility 成功");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook View.setSystemUiVisibility 失败 - " + t.getMessage());
        }
    }

    private void hookSurfaceView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(SurfaceView.class.getName(), lpparam.classLoader, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    Activity activity = getActivityFromView(view);
                    if (activity != null) {
                        XposedBridge.log(TAG + ": SurfaceView attached - " + activity.getClass().getSimpleName());
                        startGuard(activity);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook SurfaceView 失败 - " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(TextureView.class.getName(), lpparam.classLoader, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    Activity activity = getActivityFromView(view);
                    if (activity != null) {
                        XposedBridge.log(TAG + ": TextureView attached - " + activity.getClass().getSimpleName());
                        startGuard(activity);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook TextureView 失败 - " + t.getMessage());
        }
    }

    private void forceShowStatusBarFromWindow(XC_MethodHook.MethodHookParam param) {
        try {
            Window window = (Window) param.thisObject;
            Activity activity = (Activity) window.getContext();
            if (activity != null) {
                startGuard(activity);
            }
        } catch (Throwable ignored) {
        }
    }

    private Activity getActivityFromView(View view) {
        try {
            if (view.getContext() instanceof Activity) {
                return (Activity) view.getContext();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private synchronized void startGuard(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        String activityName = activity.getClass().getSimpleName();
        XposedBridge.log(TAG + ": 启动守护 - " + activityName);

        GuardTask existingTask = guardedActivities.get(activity);
        if (existingTask != null) {
            existingTask.stop();
        }

        GuardTask task = new GuardTask(activity);
        guardedActivities.put(activity, task);
        mainHandler.post(task);
    }

    private synchronized void stopGuarding(Activity activity) {
        GuardTask task = guardedActivities.remove(activity);
        if (task != null) {
            task.stop();
            XposedBridge.log(TAG + ": 停止守护 - " + activity.getClass().getSimpleName());
        }
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
            window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

            WindowManager.LayoutParams attrs = window.getAttributes();
            if (attrs != null) {
                attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                attrs.flags |= WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
                window.setAttributes(attrs);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.show(android.view.WindowInsets.Type.statusBars());
                }
            } else {
                int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                             View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                             View.SYSTEM_UI_FLAG_VISIBLE;
                decorView.setSystemUiVisibility(uiFlags);
            }

        } catch (Throwable ignored) {
        }
    }
}
