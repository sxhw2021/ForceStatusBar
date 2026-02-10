package com.example.forcestatusbar.hook;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class StatusBarHook implements IXposedHookLoadPackage {
    
    private static final String TAG = "ForceStatusBar";
    private static final int MAX_RETRY_COUNT = 5;
    
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
                            
                            // 保留 LAYOUT_FULLSCREEN 标志，让内容延伸到状态栏下方
                            // 但移除其他全屏相关的 flag
                            int oldVisibility = visibility;
                            visibility &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                            visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE;
                            visibility &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                            
                            // 添加 LAYOUT_STABLE 保持布局稳定
                            visibility |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                            
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
                        final Activity activity = (Activity) param.thisObject;
                        final Window window = activity.getWindow();
                        final View decorView = window.getDecorView();
                        
                        // 清除全屏标志
                        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                        
                        // 延迟执行，确保窗口已准备好
                        decorView.post(new Runnable() {
                            @Override
                            public void run() {
                                applyStatusBarFix(activity);
                            }
                        });
                        
                        XposedBridge.log(TAG + ": Activity.onResume - " + lpparam.packageName);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook Activity.onResume 失败 - " + e.getMessage());
        }
        
        // Hook Activity 的 onWindowFocusChanged
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class.getName(),
                lpparam.classLoader,
                "onWindowFocusChanged",
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        boolean hasFocus = (boolean) param.args[0];
                        if (hasFocus) {
                            final Activity activity = (Activity) param.thisObject;
                            activity.getWindow().getDecorView().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    applyStatusBarFix(activity);
                                }
                            }, 100);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook onWindowFocusChanged 失败 - " + e.getMessage());
        }
    }
    
    private void applyStatusBarFix(Activity activity) {
        try {
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            
            // 清除全屏标志
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            
            // 设置系统 UI 可见性
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+: 使用新的 API
                // 让内容延伸到状态栏下方，状态栏半透明覆盖
                window.setDecorFitsSystemWindows(false);
                
                // 显示状态栏
                if (window.getInsetsController() != null) {
                    window.getInsetsController().show(android.view.WindowInsets.Type.statusBars());
                }
            } else {
                // Android 10 及以下
                int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                             View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                             View.SYSTEM_UI_FLAG_VISIBLE;
                decorView.setSystemUiVisibility(uiFlags);
            }
            
            // 使用 ViewTreeObserver 监听布局变化
            applyInsetsToGameViews(decorView);
            
            XposedBridge.log(TAG + ": 应用状态栏修复 - " + activity.getPackageName());
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 应用状态栏修复失败 - " + e.getMessage());
        }
    }
    
    private void applyInsetsToGameViews(final View root) {
        try {
            root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    // 移除监听（避免重复）
                    root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    
                    // 获取状态栏高度
                    final int statusBarHeight = getStatusBarHeight(root.getContext());
                    
                    // 延迟调整视图
                    root.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // 找到游戏主视图并调整
                            View gameView = findMainGameView(root);
                            if (gameView != null) {
                                adjustGameViewWithRetry(gameView, statusBarHeight, 0);
                            }
                        }
                    }, 200);
                }
            });
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 应用 insets 失败 - " + e.getMessage());
        }
    }
    
    private void adjustGameViewWithRetry(final View view, final int statusBarHeight, final int retryCount) {
        if (retryCount >= MAX_RETRY_COUNT) return;
        
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    // 尝试调整视图的 padding
                    if (view.getPaddingTop() < statusBarHeight) {
                        view.setPadding(0, statusBarHeight, 0, 0);
                        XposedBridge.log(TAG + ": 调整视图 padding - " + statusBarHeight + "px");
                    }
                    
                    // 如果是 ViewGroup，递归调整子视图
                    if (view instanceof ViewGroup) {
                        ViewGroup viewGroup = (ViewGroup) view;
                        for (int i = 0; i < viewGroup.getChildCount(); i++) {
                            View child = viewGroup.getChildAt(i);
                            if (child instanceof SurfaceView || child instanceof TextureView) {
                                if (child.getPaddingTop() < statusBarHeight) {
                                    child.setPadding(0, statusBarHeight, 0, 0);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 重试
                    if (retryCount < MAX_RETRY_COUNT - 1) {
                        adjustGameViewWithRetry(view, statusBarHeight, retryCount + 1);
                    }
                }
            }
        }, 100L * (retryCount + 1));
    }
    
    private View findMainGameView(View view) {
        try {
            // 优先查找 SurfaceView 和 TextureView
            if (view instanceof SurfaceView || view instanceof TextureView) {
                return view;
            }
            
            // 检查是否是游戏引擎视图
            String className = view.getClass().getName();
            if (className.contains("UnityPlayer") || 
                className.contains("GLSurfaceView") ||
                className.contains("Cocos2dx") ||
                className.contains("Godot")) {
                return view;
            }
            
            // 递归查找子视图
            if (view instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) view;
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    View result = findMainGameView(viewGroup.getChildAt(i));
                    if (result != null) return result;
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return null;
    }
    
    private int getStatusBarHeight(Context context) {
        int result = 0;
        try {
            int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                result = context.getResources().getDimensionPixelSize(resourceId);
            }
        } catch (Exception e) {
            // 使用默认值（约 24dp）
            result = (int) (24 * context.getResources().getDisplayMetrics().density);
        }
        return result;
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
                                XposedBridge.log(TAG + ": 拦截 WindowInsetsControllerImpl.hide(状态栏)");
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
