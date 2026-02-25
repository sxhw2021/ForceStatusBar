package com.example.forcestatusbar.hook;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;

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
        
        // REMOVED: Display size deception - causes touch offset
        // hookDisplayMetrics(lpparam);
        // hookDisplaySize(lpparam);
        
        // Android 11+ use WindowInsetsController
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hookWindowInsetsControllerImpl(lpparam);
        }
        
        XposedBridge.log(TAG + ": Initialized WITHOUT Display deception (touch-safe) - " + lpparam.packageName);
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
                        
                        // If setting FLAG_FULLSCREEN, block it
                        if ((flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                            flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                            param.args[0] = flags;
                            XposedBridge.log(TAG + ": Intercepted setFlags FLAG_FULLSCREEN - " + lpparam.packageName);
                        }
                        
                        // Force add FLAG_FORCE_NOT_FULLSCREEN
                        if ((flags & WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN) == 0) {
                            flags |= WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
                            param.args[0] = flags;
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to hook setFlags - " + e.getMessage());
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
                            XposedBridge.log(TAG + ": Intercepted addFlags FLAG_FULLSCREEN - " + lpparam.packageName);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to hook addFlags - " + e.getMessage());
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
                            XposedBridge.log(TAG + ": Blocked clearing FLAG_FORCE_NOT_FULLSCREEN - " + lpparam.packageName);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to hook clearFlags - " + e.getMessage());
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
                                XposedBridge.log(TAG + ": Intercepted setSystemUiVisibility - " + lpparam.packageName);
                            }
                        }
                    }
                );
            } catch (Exception e) {
                XposedBridge.log(TAG + ": Failed to hook setSystemUiVisibility - " + e.getMessage());
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
                        forceContentViewFitsSystemWindows(activity);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to hook Activity.onResume - " + e.getMessage());
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
                        forceContentViewFitsSystemWindows(activity);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to hook Activity.onCreate - " + e.getMessage());
        }
        
        // Hook Activity.onPostResume
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class.getName(),
                lpparam.classLoader,
                "onPostResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        forceContentViewFitsSystemWindows(activity);
                    }
                }
            );
        } catch (Exception e) {
            // Ignore
        }
        
        // Hook Activity.onWindowFocusChanged
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
                            Activity activity = (Activity) param.thisObject;
                            forceContentViewFitsSystemWindows(activity);
                        }
                    }
                }
            );
        } catch (Exception e) {
            // Ignore
        }
        
        // Hook Window.setStatusBarColor to override with theme color
        try {
            XposedHelpers.findAndHookMethod(
                Window.class.getName(),
                lpparam.classLoader,
                "setStatusBarColor",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Window window = (Window) param.thisObject;
                        Context context = (Context) XposedHelpers.getObjectField(window, "mContext");
                        if (context instanceof Activity) {
                            Activity activity = (Activity) context;
                            int themeColor = getThemeColor(activity);
                            param.args[0] = themeColor;
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to hook setStatusBarColor - " + e.getMessage());
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
                                Class<?> insetsTypeClass = Class.forName("android.view.WindowInsets$Type");
                                statusBars = insetsTypeClass.getField("statusBars").getInt(null);
                            } catch (Exception e) {
                                // Fallback: use hardcoded status bar type
                                statusBars = 1; // Usually WindowInsets.Type.statusBars()
                            }
                            
                            // If trying to hide status bars, block it
                            if ((types & statusBars) != 0) {
                                param.setResult(null);
                                XposedBridge.log(TAG + ": Blocked WindowInsetsController.hide(statusBars) - " + lpparam.packageName);
                            }
                        }
                    });
                    
                    XposedBridge.log(TAG + ": Successfully hooked " + className + " for status bar protection");
                }
            } catch (Exception e) {
                XposedBridge.log(TAG + ": Failed to hook " + className + " - " + e.getMessage());
            }
        }
    }
    
    /**
     * Apply status bar color based on app's theme color
     */
    private void forceContentViewFitsSystemWindows(Activity activity) {
        try {
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            
            // Get app's theme color
            int statusBarColor = getThemeColor(activity);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+)
                window.setStatusBarColor(statusBarColor);
                
                View contentView = decorView.findViewById(android.R.id.content);
                if (contentView != null) {
                    contentView.setOnApplyWindowInsetsListener((v, insets) -> {
                        int statusBarHeight = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                        v.setPadding(0, statusBarHeight, 0, 0);
                        return insets;
                    });
                    contentView.requestApplyInsets();
                }
                
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Android 5.0-10 (API 21-29)
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(statusBarColor);
                
                int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                decorView.setSystemUiVisibility(flags);
                
                View contentView = decorView.findViewById(android.R.id.content);
                if (contentView != null) {
                    contentView.setFitsSystemWindows(true);
                }
                
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // Android 4.4 (API 19)
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                
                View contentView = decorView.findViewById(android.R.id.content);
                if (contentView != null) {
                    contentView.setFitsSystemWindows(true);
                }
            }
            
            XposedBridge.log(TAG + ": Applied status bar color: #" + Integer.toHexString(statusBarColor));
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed - " + e.getMessage());
        }
    }
    
    /**
     * Get app's theme color for status bar
     */
    private int getThemeColor(Activity activity) {
        int color = Color.parseColor("#212121"); // Default dark gray
        
        try {
            // First try to get from theme attributes
            int[] attrs = {android.R.attr.colorPrimaryDark, android.R.attr.colorPrimary, android.R.attr.statusBarColor, android.R.attr.windowBackground};
            android.content.res.TypedArray ta = activity.obtainStyledAttributes(attrs);
            
            color = ta.getColor(0, -1);
            if (color == -1 || color == Color.TRANSPARENT) {
                color = ta.getColor(1, -1);
            }
            if (color == -1 || color == Color.TRANSPARENT) {
                color = ta.getColor(2, -1);
            }
            if (color == -1 || color == Color.TRANSPARENT) {
                color = ta.getColor(3, -1);
            }
            ta.recycle();
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to get theme color - " + e.getMessage());
        }
        
        // If still not found, try to get from content view background
        if (color == -1 || color == Color.TRANSPARENT) {
            try {
                View contentView = activity.getWindow().getDecorView().findViewById(android.R.id.content);
                if (contentView != null) {
                    Drawable bg = contentView.getBackground();
                    if (bg instanceof ColorDrawable) {
                        color = ((ColorDrawable) bg).getColor();
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        
        if (color == -1 || color == Color.TRANSPARENT || color == 0) {
            color = Color.parseColor("#212121");
        }
        
        return color;
    }
}