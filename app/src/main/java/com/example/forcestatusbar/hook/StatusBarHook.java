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

import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class StatusBarHook implements IXposedHookLoadPackage {

    private static final String TAG = "ForceStatusBar";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final long[] DELAYS = {50, 100, 200, 500, 1000};
    private static final long MAX_GUARD_TIME = 5000;

    private static final WeakHashMap<Activity, GuardState> guardedActivities = new WeakHashMap<>();

    private static class GuardState {
        Runnable[] guardTasks;
        long startTime;
        int attempt;

        GuardState(int taskCount) {
            this.guardTasks = new Runnable[taskCount];
            this.startTime = System.currentTimeMillis();
            this.attempt = 0;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookActivityLifecycle(lpparam);
        hookWindowFlags(lpparam);
        hookViewCreation(lpparam);
        XposedBridge.log(TAG + ": 已初始化 - " + lpparam.packageName);
    }

    private void hookActivityLifecycle(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            Activity.class.getName(),
            lpparam.classLoader,
            "onCreate",
            android.os.Bundle.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    startFullGuard(activity);
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            Activity.class.getName(),
            lpparam.classLoader,
            "onResume",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    startFullGuard(activity);
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            Activity.class.getName(),
            lpparam.classLoader,
            "onWindowFocusChanged",
            boolean.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if ((boolean) param.args[0]) {
                        Activity activity = (Activity) param.thisObject;
                        startFullGuard(activity);
                    }
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            Activity.class.getName(),
            lpparam.classLoader,
            "onPause",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    stopGuarding(activity);
                }
            }
        );
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

                        Activity activity = getActivityFromWindow(param);
                        if (activity != null) {
                            startFullGuard(activity);
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

                        Activity activity = getActivityFromWindow(param);
                        if (activity != null) {
                            startFullGuard(activity);
                        }
                    }
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            Window.class.getName(),
            lpparam.classLoader,
            "clearFlags",
            int.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int flags = (int) param.args[0];
                    if ((flags & WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN) != 0) {
                        flags &= ~WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
                        param.args[0] = flags;
                        XposedBridge.log(TAG + ": 拦截 clearFlags FLAG_FORCE_NOT_FULLSCREEN");

                        Activity activity = getActivityFromWindow(param);
                        if (activity != null) {
                            startFullGuard(activity);
                        }
                    }
                }
            }
        );
    }

    private void hookViewCreation(XC_LoadPackage.LoadPackageParam lpparam) {
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
                        startFullGuard(activity);
                    }
                }
            }
        );

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
                        startFullGuard(activity);
                    }
                }
            }
        );
    }

    private Activity getActivityFromWindow(XC_MethodHook.MethodHookParam param) {
        try {
            Window window = (Window) param.thisObject;
            return (Activity) window.getContext();
        } catch (Exception e) {
            return null;
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

    private void startFullGuard(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        synchronized (guardedActivities) {
            if (guardedActivities.containsKey(activity)) {
                GuardState existing = guardedActivities.get(activity);
                if (existing != null) {
                    existing.startTime = System.currentTimeMillis();
                    existing.attempt = 0;
                }
                return;
            }

            GuardState guardState = new GuardState(DELAYS.length);

            for (int i = 0; i < DELAYS.length; i++) {
                final int index = i;
                Runnable task = new Runnable() {
                    @Override
                    public void run() {
                        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                            stopGuarding(activity);
                            return;
                        }

                        if (System.currentTimeMillis() - guardState.startTime > MAX_GUARD_TIME) {
                            stopGuarding(activity);
                            return;
                        }

                        forceShowStatusBar(activity);

                        if (index < DELAYS.length - 1) {
                            mainHandler.postDelayed(this, DELAYS[index + 1]);
                        }
                    }
                };
                guardState.guardTasks[i] = task;
            }

            guardedActivities.put(activity, guardState);
            mainHandler.post(guardState.guardTasks[0]);
            XposedBridge.log(TAG + ": 启动守护 - " + activity.getClass().getSimpleName());
        }
    }

    private void stopGuarding(Activity activity) {
        synchronized (guardedActivities) {
            GuardState state = guardedActivities.remove(activity);
            if (state != null && state.guardTasks != null) {
                for (Runnable task : state.guardTasks) {
                    if (task != null) {
                        mainHandler.removeCallbacks(task);
                    }
                }
            }
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
                window.setStatusBarColor(0x00000000);
            }

            int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                         View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                         View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                         View.SYSTEM_UI_FLAG_VISIBLE;
            decorView.setSystemUiVisibility(uiFlags);

        } catch (Exception ignored) {
        }
    }
}
