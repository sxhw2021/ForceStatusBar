package com.example.forcestatusbar.hook;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRootImpl;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class StatusBarHook implements IXposedHookLoadPackage {
    
    private static final String TAG = "ForceStatusBar";
    private int statusBarHeight = -1;
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookWindowMethods(lpparam);
        hookInputEvent(lpparam);
        hookViewRootImpl(lpparam);
        hookActivityLifecycle(lpparam);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hookWindowInsetsController(lpparam);
        }
        
        XposedBridge.log(TAG + ": 已初始化 - " + lpparam.packageName);
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
                            XposedBridge.log(TAG + ": 拦截 setFlags");
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook setFlags 失败 - " + e.getMessage());
        }
    }
    
    private void hookInputEvent(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook InputManager.injectInputEvent (系统级输入)
        try {
            Class<?> inputManagerClass = XposedHelpers.findClass(
                "android.hardware.input.InputManager",
                lpparam.classLoader
            );
            
            XposedHelpers.findAndHookMethod(
                inputManagerClass,
                "injectInputEvent",
                android.view.InputEvent.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        android.view.InputEvent event = (android.view.InputEvent) param.args[0];
                        if (event instanceof MotionEvent) {
                            MotionEvent motionEvent = (MotionEvent) event;
                            if (statusBarHeight == -1) {
                                // 延迟初始化
                                return;
                            }
                            // 调整 Y 坐标（减去状态栏高度）
                            MotionEvent newEvent = MotionEvent.obtain(motionEvent);
                            newEvent.offsetLocation(0, -statusBarHeight);
                            param.args[0] = newEvent;
                            XposedBridge.log(TAG + ": 调整输入事件坐标");
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook InputManager 失败，使用备用方案");
        }
    }
    
    private void hookViewRootImpl(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook ViewRootImpl.dispatchInputEvent
        try {
            XposedHelpers.findAndHookMethod(
                "android.view.ViewRootImpl",
                lpparam.classLoader,
                "dispatchInputEvent",
                android.view.InputEvent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        android.view.InputEvent event = (android.view.InputEvent) param.args[0];
                        if (event instanceof MotionEvent) {
                            Object viewRoot = param.thisObject;
                            View view = (View) XposedHelpers.getObjectField(viewRoot, "mView");
                            
                            if (view != null && statusBarHeight > 0) {
                                // 调整事件坐标
                                MotionEvent motionEvent = (MotionEvent) event;
                                motionEvent.offsetLocation(0, -statusBarHeight);
                            }
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook ViewRootImpl 失败 - " + e.getMessage());
        }
        
        // Hook ViewRootImpl.performTraversals - 动态调整界面
        try {
            XposedHelpers.findAndHookMethod(
                "android.view.ViewRootImpl",
                lpparam.classLoader,
                "performTraversals",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object viewRoot = param.thisObject;
                        View view = (View) XposedHelpers.getObjectField(viewRoot, "mView");
                        
                        if (view != null && isGameMainView(view)) {
                            // 延迟调整
                            view.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    adjustGameInterface(view);
                                }
                            }, 100);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook performTraversals 失败 - " + e.getMessage());
        }
    }
    
    private void hookActivityLifecycle(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Activity.onResume
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class.getName(),
                lpparam.classLoader,
                "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        final Activity activity = (Activity) param.thisObject;
                        
                        // 初始化状态栏高度
                        if (statusBarHeight == -1) {
                            statusBarHeight = getStatusBarHeight(activity);
                        }
                        
                        activity.getWindow().getDecorView().post(new Runnable() {
                            @Override
                            public void run() {
                                applyStatusBarFix(activity);
                            }
                        });
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Hook onResume 失败 - " + e.getMessage());
        }
    }
    
    private void applyStatusBarFix(Activity activity) {
        try {
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            
            // 清除全屏标志
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+
                window.setDecorFitsSystemWindows(false);
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
            
            // 调整游戏界面
            adjustGameInterface(decorView);
            
            XposedBridge.log(TAG + ": 应用状态栏修复 - " + activity.getPackageName());
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 应用状态栏修复失败 - " + e.getMessage());
        }
    }
    
    private void adjustGameInterface(View view) {
        try {
            // 检测并调整界面元素
            if (view instanceof Button || view.getClass().getName().contains("Button")) {
                adjustButton(view);
            } else if (view instanceof TextView || view.getClass().getName().contains("TextView")) {
                adjustTextView(view);
            } else if (view.isClickable() || view.isLongClickable()) {
                adjustClickableView(view);
            }
            
            // 递归调整子视图
            if (view instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) view;
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    adjustGameInterface(viewGroup.getChildAt(i));
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }
    }
    
    private void adjustButton(View button) {
        if (statusBarHeight <= 0) return;
        
        try {
            // 调整位置
            button.setTranslationY(button.getTranslationY() + statusBarHeight);
            
            // 调整触摸区域
            Rect rect = new Rect();
            button.getHitRect(rect);
            rect.top += statusBarHeight;
            rect.bottom += statusBarHeight;
            
            // 设置触摸代理
            ViewParent parent = button.getParent();
            if (parent instanceof View) {
                ((View) parent).setTouchDelegate(new android.view.TouchDelegate(rect, button));
            }
            
            XposedBridge.log(TAG + ": 调整按钮 - " + button.getClass().getName());
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 调整按钮失败 - " + e.getMessage());
        }
    }
    
    private void adjustTextView(View textView) {
        if (statusBarHeight <= 0) return;
        
        try {
            // 调整位置
            textView.setTranslationY(textView.getTranslationY() + statusBarHeight);
        } catch (Exception e) {
            // 忽略错误
        }
    }
    
    private void adjustClickableView(View view) {
        if (statusBarHeight <= 0) return;
        
        try {
            // 调整位置
            view.setTranslationY(view.getTranslationY() + statusBarHeight);
        } catch (Exception e) {
            // 忽略错误
        }
    }
    
    private boolean isGameMainView(View view) {
        String className = view.getClass().getName();
        return className.contains("UnityPlayer") ||
               className.contains("GLSurfaceView") ||
               className.contains("Cocos2dx") ||
               className.contains("Godot") ||
               className.contains("SurfaceView") ||
               className.contains("TextureView");
    }
    
    private int getStatusBarHeight(Context context) {
        if (statusBarHeight > 0) return statusBarHeight;
        
        try {
            int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
            }
        } catch (Exception e) {
            // 使用默认值
            statusBarHeight = (int) (24 * context.getResources().getDisplayMetrics().density);
        }
        return statusBarHeight;
    }
    
    private void hookWindowInsetsController(XC_LoadPackage.LoadPackageParam lpparam) {
        // Android 11+ 阻止隐藏状态栏
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
                            
                            int statusBars = 0;
                            try {
                                Class<?> typeClass = XposedHelpers.findClass(
                                    "android.view.WindowInsets$Type", 
                                    lpparam.classLoader
                                );
                                statusBars = (int) XposedHelpers.callStaticMethod(typeClass, "statusBars");
                            } catch (Exception ignored) {}
                            
                            if (statusBars != 0 && (types & statusBars) != 0) {
                                types &= ~statusBars;
                                param.args[0] = types;
                            }
                        }
                    });
                    break;
                }
            } catch (Exception e) {
                // 尝试下一个类
            }
        }
    }
}