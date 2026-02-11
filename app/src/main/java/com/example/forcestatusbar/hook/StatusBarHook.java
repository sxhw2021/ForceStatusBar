package com.example.forcestatusbar.hook;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
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
    private static Handler mainHandler = null;
    private static final WeakHashMap<Activity, Integer> activityColorMap = new WeakHashMap<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log(TAG + ": 开始 Hook " + lpparam.packageName);

        if (mainHandler == null) {
            try {
                mainHandler = new Handler(Looper.getMainLooper());
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Handler 初始化失败: " + t.getMessage());
                return;
            }
        }

        hookWindowFlags(lpparam);
        hookSystemUiVisibility(lpparam);
        hookActivityLifecycle(lpparam);
        hookSurfaceView(lpparam);

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
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    int flags = (int) param.args[0];
                    int mask = (int) param.args[1];
                    boolean modified = false;

                    if ((mask & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                        flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                        modified = true;
                    }
                    if ((mask & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0) {
                        flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                        modified = true;
                    }

                    if (modified) {
                        param.args[0] = flags;
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
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    int flags = (int) param.args[0];
                    if ((flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                        flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                        param.args[0] = flags;
                    }
                    if ((flags & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0) {
                        flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                        param.args[0] = flags;
                    }
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            Window.class.getName(),
            lpparam.classLoader,
            "setAttributes",
            WindowManager.LayoutParams.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    WindowManager.LayoutParams attrs = (WindowManager.LayoutParams) param.args[0];
                    if (attrs != null) {
                        boolean modified = false;
                        if ((attrs.flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                            attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                            modified = true;
                        }
                        if ((attrs.flags & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0) {
                            attrs.flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                            modified = true;
                        }
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
    }

    private void hookSystemUiVisibility(XC_LoadPackage.LoadPackageParam lpparam) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            XposedHelpers.findAndHookMethod(
                View.class.getName(),
                lpparam.classLoader,
                "setSystemUiVisibility",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                        int visibility = (int) param.args[0];
                        int original = visibility;

                        visibility &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
                        visibility &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                        visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE;
                        visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

                        visibility |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                        visibility |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                        visibility |= View.SYSTEM_UI_FLAG_VISIBLE;

                        if (visibility != original) {
                            param.args[0] = visibility;
                        }
                    }
                }
            );
        }
    }

    private void hookActivityLifecycle(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            Activity.class.getName(),
            lpparam.classLoader,
            "onResume",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    XposedBridge.log(TAG + ": onResume - " + activity.getClass().getSimpleName());
                    applyForceStatusBar(activity);
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
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    if ((boolean) param.args[0]) {
                        Activity activity = (Activity) param.thisObject;
                        XposedBridge.log(TAG + ": focus - " + activity.getClass().getSimpleName());
                        applyForceStatusBar(activity);
                    }
                }
            }
        );
    }

    private void hookSurfaceView(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
            SurfaceView.class.getName(),
            lpparam.classLoader,
            "onAttachedToWindow",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    Activity activity = getActivityFromView(view);
                    if (activity != null) {
                        XposedBridge.log(TAG + ": SurfaceView - " + activity.getClass().getSimpleName());
                        applyForceStatusBar(activity);
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
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    View view = (View) param.thisObject;
                    Activity activity = getActivityFromView(view);
                    if (activity != null) {
                        XposedBridge.log(TAG + ": TextureView - " + activity.getClass().getSimpleName());
                        applyForceStatusBar(activity);
                    }
                }
            }
        );
    }

    private Activity getActivityFromView(View view) {
        try {
            if (view.getContext() instanceof Activity) {
                return (Activity) view.getContext();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void applyForceStatusBar(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (mainHandler == null) return;

        mainHandler.post(() -> {
            try {
                forceShowStatusBar(activity);
                updateStatusBarColor(activity);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": apply 失败: " + t.getMessage());
            }
        });
    }

    private void forceShowStatusBar(Activity activity) {
        try {
            Window window = activity.getWindow();
            if (window == null) return;

            View decorView = window.getDecorView();
            if (decorView == null) return;

            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

            WindowManager.LayoutParams attrs = window.getAttributes();
            if (attrs != null) {
                attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                attrs.flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
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
                decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_VISIBLE
                );
            }

        } catch (Throwable ignored) {}
    }

    private void updateStatusBarColor(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        try {
            Window window = activity.getWindow();
            if (window == null) return;

            int color = extractColorFromActivity(activity);
            int statusBarColor = Color.argb(180, Color.red(color), Color.green(color), Color.blue(color));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setStatusBarColor(statusBarColor);
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    boolean isLight = isLightColor(color);
                    if (isLight) {
                        controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        );
                    } else {
                        controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.setStatusBarColor(statusBarColor);
                View decorView = window.getDecorView();
                int flags = decorView.getSystemUiVisibility();
                boolean isLight = isLightColor(color);
                if (isLight) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                decorView.setSystemUiVisibility(flags);
            }

            XposedBridge.log(TAG + ": 颜色 RGB(" + Color.red(color) + "," + Color.green(color) + "," + Color.blue(color) + ")");

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 颜色设置失败: " + t.getMessage());
        }
    }

    private int extractColorFromActivity(Activity activity) {
        try {
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            android.view.ViewGroup contentView = (android.view.ViewGroup) decorView.findViewById(android.R.id.content);

            if (contentView != null && contentView.getChildCount() > 0) {
                View rootView = contentView.getChildAt(0);
                if (rootView != null) {
                    android.graphics.drawable.Drawable bg = rootView.getBackground();
                    if (bg instanceof android.graphics.drawable.ColorDrawable) {
                        return ((android.graphics.drawable.ColorDrawable) bg).getColor();
                    }
                }
            }
            return Color.parseColor("#CC000000");
        } catch (Throwable e) {
            return Color.parseColor("#CC000000");
        }
    }

    private boolean isLightColor(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return luminance > 0.5;
    }
}
