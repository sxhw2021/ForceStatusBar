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
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class StatusBarHook implements IXposedHookLoadPackage {
    
    private static final String TAG = "ForceStatusBar";
    private static Handler mainHandler = null;
    
    private static final java.util.Map<View, GuardInfo> guardedViews = new WeakHashMap<>();
    
    private static class GuardInfo {
        final Activity activity;
        long lastCheck;
        boolean isActive;
        
        GuardInfo(Activity activity) {
            this.activity = activity;
            this.lastCheck = System.currentTimeMillis();
            this.isActive = true;
        }
    }
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log(TAG + ": 开始 Hook " + lpparam.packageName);
        
        // 延迟初始化 Handler
        if (mainHandler == null) {
            try {
                mainHandler = new Handler(Looper.getMainLooper());
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Handler 初始化失败: " + t.getMessage());
                return;
            }
        }
        
        hookWindowMethods(lpparam);
        hookDecorView(lpparam);
        hookActivityLifecycle(lpparam);
        hookViewCreation(lpparam);
        
        // Android 11+ use WindowInsetsController
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hookWindowInsetsControllerImpl(lpparam);
        }
        
        XposedBridge.log(TAG + ": 已初始化 - " + lpparam.packageName);
    }
    
    private void hookWindowMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Window.setFlags method
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
                        
                        // Check if FLAG_FULLSCREEN is set
                        if ((mask & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                            flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                            modified = true;
                        }
                        
                        // Remove FLAG_LAYOUT_NO_LIMITS
                        if ((mask & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0) {
                            flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                            modified = true;
                        }
                        
                        if (modified) {
                            param.args[0] = flags;
                            XposedBridge.log(TAG + ": 拦截 setFlags - " + lpparam.packageName);
                            
                            Activity activity = getActivityFromWindow(param);
                            if (activity != null) {
                                startGuarding(activity);
                            }
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook setFlags 失败: " + e.getMessage());
        }
        
        // Hook addFlags method
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
                        
                        // If adding FLAG_FULLSCREEN, block it
                        if ((flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                            flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                            param.args[0] = flags;
                            XposedBridge.log(TAG + ": 拦截 addFlags FLAG_FULLSCREEN - " + lpparam.packageName);
                            
                            Activity activity = getActivityFromWindow(param);
                            if (activity != null) {
                                startGuarding(activity);
                            }
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook addFlags 失败: " + e.getMessage());
        }
        
        // Hook clearFlags method
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
                        
                        // If trying to clear FLAG_FORCE_NOT_FULLSCREEN, block it
                        if ((flags & WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN) != 0) {
                            flags &= ~WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
                            param.args[0] = flags;
                            XposedBridge.log(TAG + ": 拦截 clearFlags FLAG_FORCE_NOT_FULLSCREEN - " + lpparam.packageName);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook clearFlags 失败: " + e.getMessage());
        }
        
        // Hook setAttributes method
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
                            
                            // Remove fullscreen flags
                            if ((attrs.flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                                attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                                modified = true;
                            }
                            
                            if ((attrs.flags & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0) {
                                attrs.flags &= ~WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                                modified = true;
                            }
                            
                            // Force add FLAG_FORCE_NOT_FULLSCREEN
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
            XposedBridge.log(TAG + ": Hook setAttributes 失败: " + e.getMessage());
        }
    }
    
    private void hookDecorView(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook DecorView.setSystemUiVisibility for Android 10 and below
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
                            
                            // Remove fullscreen related flags
                            int oldVisibility = visibility;
                            visibility &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
                            visibility &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                            visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE;
                            visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                            
                            // Ensure status bar is visible
                            visibility |= View.SYSTEM_UI_FLAG_VISIBLE;
                            
                            if (visibility != oldVisibility) {
                                param.args[0] = visibility;
                                XposedBridge.log(TAG + ": 拦截 setSystemUiVisibility - " + lpparam.packageName);
                            }
                        }
                    }
                );
            } catch (Exception e) {
                XposedBridge.log(TAG + ": Hook setSystemUiVisibility 失败: " + e.getMessage());
            }
        }
    }
    
    private void hookActivityLifecycle(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Activity.onResume to ensure status bar is visible
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class.getName(),
                lpparam.classLoader,
                "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        XposedBridge.log(TAG + ": onResume " + activity.getClass().getSimpleName());
                        startGuarding(activity);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook Activity.onResume 失败: " + e.getMessage());
        }
        
        // Hook Activity.onCreate to set up from beginning
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
                        
                        // Force add FLAG_FORCE_NOT_FULLSCREEN
                        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                        
                        // Android 11+ enable Edge-to-Edge
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            window.setDecorFitsSystemWindows(false);
                        }
                        
                        XposedBridge.log(TAG + ": onCreate set status bar flags - " + lpparam.packageName);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook Activity.onCreate 失败: " + e.getMessage());
        }
    }
    
    private void hookViewCreation(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook SurfaceView creation - 游戏引擎常用
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
                            XposedBridge.log(TAG + ": SurfaceView attached - " + activity.getClass().getSimpleName());
                            startGuarding(activity);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook SurfaceView 失败: " + e.getMessage());
        }

        // Hook TextureView creation
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
                            XposedBridge.log(TAG + ": TextureView attached - " + activity.getClass().getSimpleName());
                            startGuarding(activity);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook TextureView 失败: " + e.getMessage());
        }
    }
    
    private void hookWindowInsetsControllerImpl(XC_LoadPackage.LoadPackageParam lpparam) {
        // Android 11+ try to hook WindowInsetsController implementation classes
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
                            
                            // Try to get statusBars type
                            int statusBars = 0;
                            try {
                                Class<?> typeClass = XposedHelpers.findClass(
                                    "android.view.WindowInsets$Type", 
                                    lpparam.classLoader
                                );
                                statusBars = (int) XposedHelpers.callStaticMethod(typeClass, "statusBars");
                            } catch (Exception ignored) {}
                            
                            // If trying to hide status bar, block it
                            if (statusBars != 0 && (types & statusBars) != 0) {
                                types &= ~statusBars;
                                param.args[0] = types;
                                XposedBridge.log(TAG + ": 拦截 WindowInsetsControllerImpl.hide(status bar) - " + lpparam.packageName);
                            }
                        }
                    });
                    
                    XposedBridge.log(TAG + ": 成功 Hook WindowInsetsController - " + className);
                    break; // Exit after success
                }
            } catch (Exception e) {
                // Try next class
            }
        }
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

    private void startGuarding(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        synchronized (guardedViews) {
            GuardInfo existing = guardedViews.get(activity);
            if (existing != null) {
                existing.lastCheck = System.currentTimeMillis();
                existing.isActive = true;
                return;
            }

            GuardInfo guardInfo = new GuardInfo(activity);
            guardedViews.put(activity, guardInfo);

            if (mainHandler != null) {
                mainHandler.post(() -> {
                    try {
                        forceShowStatusBar(activity);
                        adjustRootViewPadding(activity);
                        setupDynamicStatusBarColor(activity);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": 初始化失败: " + t.getMessage());
                    }
                });
            }
        }
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
                int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                             View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                             View.SYSTEM_UI_FLAG_VISIBLE;
                decorView.setSystemUiVisibility(uiFlags);
            }

        } catch (Throwable ignored) {
        }
    }
    
    /**
     * 改进的 root 视图调整，解决触摸偏移问题
     */
    private void adjustRootViewPadding(Activity activity) {
        try {
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            android.view.ViewGroup contentView = (android.view.ViewGroup) decorView.findViewById(android.R.id.content);
            
            if (contentView == null || contentView.getChildCount() == 0) {
                return;
            }
            
            // Get the root view
            View rootView = contentView.getChildAt(0);
            if (rootView == null) {
                return;
            }
            
            // 获取状态栏高度（更精确）
            int statusBarHeight = getStatusBarHeight(activity);
            
            if (statusBarHeight > 0) {
                // 保存原始 padding
                if (rootView.getTag() == null) {
                    rootView.setTag(new int[]{
                        rootView.getPaddingLeft(),
                        rootView.getPaddingTop(),
                        rootView.getPaddingRight(),
                        rootView.getPaddingBottom()
                    });
                }
                
                int[] originalPadding = (int[]) rootView.getTag();
                
                // 获取当前屏幕方向和实际边距
                boolean isLandscape = isLandscapeOrientation(activity);
                int actualStatusBarHeight = 0;
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowInsets insets = decorView.getRootWindowInsets();
                    if (insets != null) {
                        actualStatusBarHeight = insets.getStableInsetTop();
                    }
                }
                
                // 如果获取不到实际高度，使用计算值
                if (actualStatusBarHeight == 0) {
                    actualStatusBarHeight = statusBarHeight;
                }
                
                // 修正触摸偏移：横屏时减少 padding，竖屏时精确匹配
                int topPadding = 0;
                if (isLandscape) {
                    // 横屏：状态栏通常在右侧或顶部，最小化顶部偏移
                    topPadding = Math.min(actualStatusBarHeight / 8, 2);
                } else {
                    // 竖屏：状态栏在顶部，需要完整的高度避免遮挡
                    topPadding = actualStatusBarHeight;
                }
                
                // 应用调整后的 padding
                rootView.setPadding(
                    originalPadding[0],
                    originalPadding[1] + topPadding,
                    originalPadding[2],
                    originalPadding[3]
                );
                
                XposedBridge.log(TAG + ": 应用 padding 调整 - 横屏: " + isLandscape + 
                          ", Top padding: " + topPadding + "px, 实际状态栏高度: " + actualStatusBarHeight + "px");
            }
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": Padding 调整失败: " + e.getMessage());
        }
    }
    
    /**
     * 设置动态状态栏颜色
     */
    private void setupDynamicStatusBarColor(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        try {
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 使用新的 API
                setupDynamicColorModern(activity, window, decorView);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0+ 适配旧版
                setupDynamicColorLegacy(activity, window, decorView);
            }
            
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": 动态颜色设置失败: " + e.getMessage());
        }
    }

    private void setupDynamicColorModern(Activity activity, Window window, View decorView) {
        try {
            // 设置透明状态栏初始状态
            window.setStatusBarColor(Color.TRANSPARENT);
            
            WindowInsetsController controller = window.getInsetsController();
            if (controller == null) return;
            
            // 立即提取并应用状态栏颜色
            mainHandler.postDelayed(() -> {
                try {
                    int dominantColor = extractDominantColor(decorView);
                    int statusBarColor = adaptColorForStatusBar(dominantColor);
                    window.setStatusBarColor(statusBarColor);
                    
                    // 自动调整状态栏文字颜色
                    boolean isLightContent = isLightColor(dominantColor);
                    if (isLightContent) {
                        controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        );
                    } else {
                        controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                    }
                    
                    XposedBridge.log(TAG + ": 动态状态栏颜色 - RGB(" + 
                              Color.red(dominantColor) + "," + Color.green(dominantColor) + "," + 
                              Color.blue(dominantColor) + ")");
                } catch (Throwable ignored) {
                }
            }, 300);
            
        } catch (Throwable ignored) {
        }
    }

    private void setupDynamicColorLegacy(Activity activity, Window window, View decorView) {
        try {
            // 设置透明状态栏初始状态
            window.setStatusBarColor(Color.TRANSPARENT);
            
            // 立即提取并应用状态栏颜色
            mainHandler.postDelayed(() -> {
                try {
                    int dominantColor = extractDominantColor(decorView);
                    int statusBarColor = adaptColorForStatusBar(dominantColor);
                    window.setStatusBarColor(statusBarColor);
                    
                    // 自动调整状态栏文字颜色
                    View systemUiView = decorView;
                    int flags = systemUiView.getSystemUiVisibility();
                    boolean isLightContent = isLightColor(dominantColor);
                    
                    if (isLightContent) {
                        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    } else {
                        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    }
                    systemUiView.setSystemUiVisibility(flags);
                    
                    XposedBridge.log(TAG + ": 动态状态栏颜色 (Legacy) - RGB(" + 
                              Color.red(dominantColor) + "," + Color.green(dominantColor) + "," + 
                              Color.blue(dominantColor) + ")");
                } catch (Throwable ignored) {
                }
            }, 300);
            
        } catch (Throwable ignored) {
        }
    }

    /**
     * 提取视图的主色调
     */
    private int extractDominantColor(View view) {
        try {
            // 尝试获取背景颜色
            if (view.getBackground() instanceof android.graphics.drawable.ColorDrawable) {
                android.graphics.drawable.ColorDrawable colorDrawable = 
                    (android.graphics.drawable.ColorDrawable) view.getBackground();
                return colorDrawable.getColor();
            }
            
            // 如果没有背景色，尝试从子视图获取
            if (view instanceof android.view.ViewGroup) {
                android.view.ViewGroup viewGroup = (android.view.ViewGroup) view;
                for (int i = 0; i < Math.min(viewGroup.getChildCount(), 3); i++) {
                    View child = viewGroup.getChildAt(i);
                    if (child != null && child.getBackground() instanceof android.graphics.drawable.ColorDrawable) {
                        android.graphics.drawable.ColorDrawable childDrawable = 
                            (android.graphics.drawable.ColorDrawable) child.getBackground();
                        return childDrawable.getColor();
                    }
                }
            }
            
            // 默认：深色半透明
            return Color.parseColor("#88000000");
        } catch (Throwable e) {
            return Color.parseColor("#88000000");
        }
    }

    /**
     * 判断颜色是否为浅色
     */
    private boolean isLightColor(int color) {
        double luminance = (0.299 * Color.red(color) + 
                          0.587 * Color.green(color) + 
                          0.114 * Color.blue(color)) / 255;
        return luminance > 0.6;
    }

    /**
     * 调整颜色以适应状态栏
     */
    private int adaptColorForStatusBar(int originalColor) {
        // 增加透明度以确保可读性
        int alpha = Math.max(Color.alpha(originalColor), 200); // 至少 78% 不透明度
        return Color.argb(alpha, Color.red(originalColor), 
                               Color.green(originalColor), Color.blue(originalColor));
    }
    
    /**
     * 检查设备是否为横屏方向
     */
    private boolean isLandscapeOrientation(Activity activity) {
        try {
            int orientation = activity.getResources().getConfiguration().orientation;
            return orientation == Configuration.ORIENTATION_LANDSCAPE;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 使用多种方法获取状态栏高度
     */
    private int getStatusBarHeight(Context context) {
        try {
            // 方法1：系统资源
            int result = 0;
            int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                result = context.getResources().getDimensionPixelSize(resourceId);
            }
            
            // 方法2：内部资源（fallback）
            if (result == 0) {
                try {
                    Class<?> c = Class.forName("com.android.internal.R$dimen");
                    Object obj = c.newInstance();
                    java.lang.reflect.Field field = c.getField("status_bar_height");
                    result = context.getResources().getDimensionPixelSize(Integer.parseInt(field.get(obj).toString()));
                } catch (Exception ignored) {}
            }
            
            // 方法3：基于屏幕密度的默认值
            if (result == 0) {
                DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                float dp = metrics.density <= 1.0f ? 24f : 
                          metrics.density <= 2.0f ? 25f : 
                          metrics.density <= 3.0f ? 26f : 28f;
                result = (int) (dp * metrics.density + 0.5f);
            }
            
            return result;
        } catch (Exception e) {
            // 最后的 fallback：24dp
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            return (int) (24 * metrics.density + 0.5f);
        }
    }
}