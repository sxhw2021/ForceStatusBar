package com.example.forcestatusbar.hook;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
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
                        
                        // Check if FLAG_FULLSCREEN is set
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
        // Hook Activity 的 onResume，确保状态栏显示
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
                        
                        // 启用 Edge-to-Edge 布局（Android 11+）
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            window.setDecorFitsSystemWindows(false);
                        }
                        
                        // 设置系统 UI 可见性并调整 padding 避免遮挡
                        View decorView = window.getDecorView();
                        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                        
                        // 智能调整根布局，避免内容被遮挡同时保持触摸坐标正确
                        adjustRootViewPadding(activity);
                        
                        XposedBridge.log(TAG + ": Activity.onResume 强制显示状态栏 - " + lpparam.packageName);
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
                        
                        // Android 11+ 启用 Edge-to-Edge
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            window.setDecorFitsSystemWindows(false);
                        }
                        
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
    
    /**
     * Simple approach: adjust root layout padding to avoid status bar overlap
     * This is a more reliable solution that avoids complex layout manipulation
     */
    private void adjustRootViewPadding(Activity activity) {
        try {
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            ViewGroup contentView = (ViewGroup) decorView.findViewById(android.R.id.content);
            
            if (contentView == null || contentView.getChildCount() == 0) {
                return;
            }
            
            // Get the root view and apply simple padding adjustment
            View rootView = contentView.getChildAt(0);
            int statusBarHeight = getStatusBarHeight(activity);
            
            if (rootView != null && statusBarHeight > 0) {
                // Store original padding if not already stored
                if (rootView.getTag() == null) {
                    rootView.setTag(new int[]{
                        rootView.getPaddingLeft(),
                        rootView.getPaddingTop(),
                        rootView.getPaddingRight(),
                        rootView.getPaddingBottom()
                    });
                }
                
                int[] originalPadding = (int[]) rootView.getTag();
                
                // Simple padding adjustment for all versions
                rootView.setPadding(
                    originalPadding[0],
                    originalPadding[1] + statusBarHeight,
                    originalPadding[2],
                    originalPadding[3]
                );
                
                XposedBridge.log(TAG + ": Applied status bar padding adjustment - " + statusBarHeight + "px");
            }
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to adjust padding - " + e.getMessage());
        }
    }
            
            // 采用更兼容的方案：修改布局参数而不是 padding
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 使用现代 WindowInsets API + 布局偏移
                adjustWithInsetsAPI(activity, decorView, contentView);
            } else {
                // Android 10 及以下使用传统方法
                adjustWithLegacyMethod(activity, decorView, contentView);
            }
            
            XposedBridge.log(TAG + ": 已配置状态栏布局调整 - " + activity.getClass().getSimpleName());
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 智能布局调整失败，使用备用方案 - " + e.getMessage());
            // 备用方案：使用简单的 padding 调整
            applySimplePaddingFallback(activity);
        }
    }
    
    
    
    /**
     * 使用 WindowInsets API 进行精确布局调整（Android 11+）
     */
    private void adjustWithInsetsAPI(Activity activity, View decorView, ViewGroup contentView) {
        decorView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                try {
                    int statusBarInset = insets.getSystemWindowInsetTop();
                    
                    if (statusBarInset > 0) {
                        // 遍历所有直接子视图，智能调整
                        adjustContentViewsInsets(contentView, statusBarInset, activity);
                        XposedBridge.log(TAG + ": Android 11+ 智能布局调整 inset=" + statusBarInset + "px");
                    }
                } catch (Exception e) {
                    XposedBridge.log(TAG + ": WindowInsets 调整失败 - " + e.getMessage());
                }
                
                return insets;
            }
        });
        
        decorView.requestApplyInsets();
    }
    
    /**
     * 传统方法调整（Android 10 及以下）
     */
    private void adjustWithLegacyMethod(Activity activity, View decorView, ViewGroup contentView) {
        int statusBarHeight = getStatusBarHeight(activity);
        if (statusBarHeight > 0) {
            adjustContentViewsInsets(contentView, statusBarHeight, activity);
            XposedBridge.log(TAG + ": 传统方法布局调整 height=" + statusBarHeight + "px");
        }
    }
    
    /**
     * 智能调整内容视图的 insets，避免触摸坐标偏移
     */
    private void adjustContentViewsInsets(ViewGroup parent, int insetTop, Activity activity) {
        try {
            // 方案1：对于特定布局类型使用不同的策略
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                
                if (child instanceof ViewGroup) {
                    ViewGroup childGroup = (ViewGroup) child;
                    
                    // 保存原始布局信息
                    Object originalInfo = child.getTag(ResourceIds.original_layout_info);
                    if (originalInfo == null) {
                        child.setTag(ResourceIds.original_layout_info, new LayoutInfo(
                            child.getPaddingLeft(),
                            child.getPaddingTop(), 
                            child.getPaddingRight(),
                            child.getPaddingBottom(),
                            child.getLayoutParams()
                        ));
                    }
                    
                    LayoutInfo info = (LayoutInfo) child.getTag(ResourceIds.original_layout_info);
                    
                    // 根据视图类型选择调整策略
                    if (shouldUseMarginStrategy(child)) {
                        // 使用 margin 策略：不影响触摸坐标
                        adjustWithMargin(childGroup, insetTop, info);
                    } else {
                        // 使用 padding 策略：仅用于支持 insets 的视图
                        adjustWithPadding(childGroup, insetTop, info);
                    }
                }
            }
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 智能布局调整失败 - " + e.getMessage());
        }
    }
    
    /**
     * 判断是否应该使用 margin 策略
     */
    private boolean shouldUseMarginStrategy(View view) {
        // 对于常见的布局容器，优先使用 margin 避免触摸坐标问题
        String className = view.getClass().getSimpleName();
        return className.contains("LinearLayout") || 
               className.contains("FrameLayout") ||
               className.contains("RelativeLayout") ||
               className.contains("ConstraintLayout");
    }
    
    /**
     * 使用 margin 调整（推荐，不影响触摸坐标）
     */
    private void adjustWithMargin(ViewGroup view, int insetTop, LayoutInfo originalInfo) {
        try {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            
            // 保存原始 margin
            if (originalInfo.originalMarginTop == -1) {
                originalInfo.originalMarginTop = params.topMargin;
            }
            
            // 应用新的 margin
            params.topMargin = originalInfo.originalMarginTop + insetTop;
            view.setLayoutParams(params);
            
            // 恢复原始 padding
            view.setPadding(
                originalInfo.originalPaddingLeft,
                originalInfo.originalPaddingTop,
                originalInfo.originalPaddingRight,
                originalInfo.originalPaddingBottom
            );
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Margin 调整失败 - " + e.getMessage());
        }
    }
    
    /**
     * 使用 padding 调整（备选方案）
     */
    private void adjustWithPadding(ViewGroup view, int insetTop, LayoutInfo originalInfo) {
        try {
            view.setPadding(
                originalInfo.originalPaddingLeft,
                originalInfo.originalPaddingTop + insetTop,
                originalInfo.originalPaddingRight,
                originalInfo.originalPaddingBottom
            );
            
            // 恢复原始 margin
            if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                if (originalInfo.originalMarginTop != -1) {
                    params.topMargin = originalInfo.originalMarginTop;
                    view.setLayoutParams(params);
                }
            }
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Padding 调整失败 - " + e.getMessage());
        }
    }
    
    /**
     * Get status bar height using multiple fallback methods
     */
    private int getStatusBarHeight(Context context) {
        try {
            // Method 1: System resources
            int result = 0;
            int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                result = context.getResources().getDimensionPixelSize(resourceId);
            }
            
            // Method 2: Internal resources (fallback)
            if (result == 0) {
                try {
                    Class<?> c = Class.forName("com.android.internal.R$dimen");
                    Object obj = c.newInstance();
                    java.lang.reflect.Field field = c.getField("status_bar_height");
                    result = context.getResources().getDimensionPixelSize(Integer.parseInt(field.get(obj).toString()));
                } catch (Exception ignored) {}
            }
            
            // Method 3: Default based on screen density
            if (result == 0) {
                DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                float dp = metrics.density <= 1.0f ? 24f : 
                          metrics.density <= 2.0f ? 25f : 
                          metrics.density <= 3.0f ? 26f : 28f;
                result = (int) (dp * metrics.density + 0.5f);
            }
            
            return result;
        } catch (Exception e) {
            // Final fallback: 24dp
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            return (int) (24 * metrics.density + 0.5f);
        }
    }
    

    
    
}
