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
                        
                        // 为根布局添加状态栏高度 padding，避免内容被遮挡
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
     * 调整根布局 padding 以避免被状态栏遮挡
     * 参考 immersionbar 库的 Edge-to-Edge 实现方式
     */
    private void adjustRootViewPadding(Activity activity) {
        try {
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            ViewGroup contentView = (ViewGroup) decorView.findViewById(android.R.id.content);
            
            if (contentView == null || contentView.getChildCount() == 0) {
                return;
            }
            
            ViewGroup rootLayout = (ViewGroup) contentView.getChildAt(0);
            
            // 保存原始 padding（只保存一次）
            if (rootLayout.getTag() == null) {
                rootLayout.setTag(new int[]{
                    rootLayout.getPaddingLeft(),
                    rootLayout.getPaddingTop(),
                    rootLayout.getPaddingRight(),
                    rootLayout.getPaddingBottom()
                });
            }
            
            int[] originalPadding = (int[]) rootLayout.getTag();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 使用现代 WindowInsets API
                decorView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                        try {
                            // 获取状态栏 inset
                            int statusBarInset = insets.getSystemWindowInsetTop();
                            
                            if (statusBarInset > 0) {
                                // 应用 padding 避开状态栏
                                rootLayout.setPadding(
                                    originalPadding[0],
                                    originalPadding[1] + statusBarInset,
                                    originalPadding[2],
                                    originalPadding[3]
                                );
                                
                                XposedBridge.log(TAG + ": Android 11+ 状态栏 padding=" + statusBarInset + "px");
                            }
                        } catch (Exception e) {
                            // 降级到传统方法
                            applyFallbackPadding(rootLayout, originalPadding, activity);
                        }
                        
                        return insets;
                    }
                });
                
                // 立即触发 insets 计算
                decorView.requestApplyInsets();
                
            } else {
                // Android 10 及以下使用传统方法
                applyFallbackPadding(rootLayout, originalPadding, activity);
            }
            
            XposedBridge.log(TAG + ": 已配置状态栏 padding 调整 - " + activity.getClass().getSimpleName());
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 调整 padding 失败 - " + e.getMessage());
        }
    }
    
    /**
     * 备用 padding 应用方法（用于 Android 10 及以下或出错时）
     */
    private void applyFallbackPadding(ViewGroup rootLayout, int[] originalPadding, Activity activity) {
        try {
            int statusBarHeight = getStatusBarHeight(activity);
            
            if (statusBarHeight > 0) {
                rootLayout.setPadding(
                    originalPadding[0],
                    originalPadding[1] + statusBarHeight,
                    originalPadding[2],
                    originalPadding[3]
                );
                
                XposedBridge.log(TAG + ": 备用方法状态栏 padding=" + statusBarHeight + "px");
            }
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 备用 padding 方法失败 - " + e.getMessage());
        }
    }
    
    /**
     * 获取状态栏高度
     * 参考 immersionbar 库的实现方式
     */
    private int getStatusBarHeight(Context context) {
        try {
            // 方法1：通过系统资源获取
            int result = 0;
            int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                result = context.getResources().getDimensionPixelSize(resourceId);
            }
            
            // 方法2：通过内部资源获取（备用）
            if (result == 0) {
                try {
                    Class<?> c = Class.forName("com.android.internal.R$dimen");
                    Object obj = c.newInstance();
                    java.lang.reflect.Field field = c.getField("status_bar_height");
                    result = context.getResources().getDimensionPixelSize(Integer.parseInt(field.get(obj).toString()));
                } catch (Exception ignored) {}
            }
            
            // 方法3：通过 WindowInsets 获取（Android 11+）
            if (result == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    WindowInsets insets = ((Activity) context).getWindowManager().getCurrentWindowMetrics().getWindowInsets();
                    result = insets.getSystemWindowInsetTop();
                } catch (Exception ignored) {}
            }
            
            // 方法4：使用智能默认值
            if (result == 0) {
                DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                // 根据屏幕密度智能估算
                float dp = metrics.density <= 1.0f ? 24f : 
                          metrics.density <= 2.0f ? 25f : 
                          metrics.density <= 3.0f ? 26f : 28f;
                result = (int) (dp * metrics.density + 0.5f);
            }
            
            return result;
        } catch (Exception e) {
            // 最终默认值：24dp
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            return (int) (24 * metrics.density + 0.5f);
        }
    }
    
    /**
     * 恢复根布局原始 padding
     */
    private void restoreRootViewPadding(Activity activity) {
        try {
            View decorView = activity.getWindow().getDecorView();
            ViewGroup contentView = (ViewGroup) decorView.findViewById(android.R.id.content);
            if (contentView != null && contentView.getChildCount() > 0) {
                ViewGroup rootLayout = (ViewGroup) contentView.getChildAt(0);
                if (rootLayout != null && rootLayout.getTag() != null) {
                    int[] originalPadding = (int[]) rootLayout.getTag();
                    rootLayout.setPadding(
                        originalPadding[0],
                        originalPadding[1],
                        originalPadding[2],
                        originalPadding[3]
                    );
                    XposedBridge.log(TAG + ": 已恢复根布局原始 padding - " + activity.getClass().getSimpleName());
                }
            }
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 恢复 padding 失败 - " + e.getMessage());
        }
    }
}
