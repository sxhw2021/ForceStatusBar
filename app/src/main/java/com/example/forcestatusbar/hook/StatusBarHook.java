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

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class StatusBarHook implements IXposedHookLoadPackage {

    private static final String TAG = "ForceStatusBar";
    private static Handler mainHandler = null;

    private static final long MAX_GUARD_TIME = 8000;

    private static final java.util.Map<View, GuardTask> guardedViews = new java.util.WeakHashMap<>();

    private class GuardTask implements Runnable {
        private final View view;
        private final long startTime;
        private volatile boolean stopped = false;

        GuardTask(View view) {
            this.view = view;
            this.startTime = System.currentTimeMillis();
        }

        @Override
        public void run() {
            if (stopped || view == null) {
                stopGuarding(view);
                return;
            }

            if (System.currentTimeMillis() - startTime > MAX_GUARD_TIME) {
                stopGuarding(view);
                return;
            }

            try {
                Activity activity = getActivityFromView(view);
                if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                    forceShowStatusBar(activity);
                } else {
                    stopGuarding(view);
                    return;
                }
            } catch (Throwable ignored) {
                stopGuarding(view);
                return;
            }

            if (!stopped && mainHandler != null) {
                mainHandler.postDelayed(this, 150);
            }
        }

        public void stop() {
            stopped = true;
            if (mainHandler != null) {
                mainHandler.removeCallbacks(this);
            }
        }
    }

    private synchronized Handler getMainHandler() {
        if (mainHandler == null) {
            try {
                mainHandler = new Handler(Looper.getMainLooper());
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Handler创建失败: " + t.getMessage());
            }
        }
        return mainHandler;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log(TAG + ": 开始 Hook " + lpparam.packageName);

        getMainHandler();

        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    XposedBridge.log(TAG + ": onResume " + activity.getClass().getSimpleName());
                    startGuardForActivity(activity);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook onResume 失败: " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onWindowFocusChanged", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if ((boolean) param.args[0]) {
                        Activity activity = (Activity) param.thisObject;
                        XposedBridge.log(TAG + ": focus " + activity.getClass().getSimpleName());
                        startGuardForActivity(activity);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook onWindowFocusChanged 失败: " + t.getMessage());
        }

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
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook setFlags 失败: " + t.getMessage());
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
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook addFlags 失败: " + t.getMessage());
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
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook setSystemUiVisibility 失败: " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(SurfaceView.class.getName(), lpparam.classLoader, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    XposedBridge.log(TAG + ": SurfaceView attached");
                    startGuardForView(view);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook SurfaceView 失败: " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(TextureView.class.getName(), lpparam.classLoader, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    XposedBridge.log(TAG + ": TextureView attached");
                    startGuardForView(view);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook TextureView 失败: " + t.getMessage());
        }

        XposedBridge.log(TAG + ": 已初始化 " + lpparam.packageName);
    }

    private void startGuardForActivity(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        try {
            Window window = activity.getWindow();
            if (window != null) {
                View decorView = window.getDecorView();
                if (decorView != null) {
                    startGuardForView(decorView);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private synchronized void startGuardForView(View view) {
        if (view == null) return;

        GuardTask existingTask = guardedViews.get(view);
        if (existingTask != null) {
            existingTask.stop();
        }

        GuardTask task = new GuardTask(view);
        guardedViews.put(view, task);
        Handler handler = getMainHandler();
        if (handler != null) {
            handler.post(task);
        }
    }

    private synchronized void stopGuarding(View view) {
        GuardTask task = guardedViews.remove(view);
        if (task != null) {
            task.stop();
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

    private void forceShowStatusBar(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
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
