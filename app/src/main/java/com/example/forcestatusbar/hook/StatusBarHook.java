package com.example.forcestatusbar.hook;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.graphics.Point;

import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.Window;
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
                        Window window = activity.getWindow();
                        
                        // Simple and safe: Force status bar visible
                        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                        
                        // For older versions, set system UI visibility
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            View decorView = window.getDecorView();
                            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                        }
                        
                        // Force content view to handle system insets properly (includes Android R+ handling)
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
                        Window window = activity.getWindow();
                        
                        // Force status bar visibility
                        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                        
                        // Force content view to handle system insets properly (includes Android R+ handling)
                        forceContentViewFitsSystemWindows(activity);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to hook Activity.onCreate - " + e.getMessage());
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
     * Force content view to fit system windows properly
     * FIXED: Properly handle WindowInsets to prevent content overlap
     */
    private void forceContentViewFitsSystemWindows(Activity activity) {
        try {
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            
            // For Android 11+, use proper WindowInsets handling
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // IMPORTANT: setDecorFitsSystemWindows(false) to handle insets manually
                window.setDecorFitsSystemWindows(false);
                
                View contentView = decorView.findViewById(android.R.id.content);
                if (contentView != null) {
                    // Add listener to handle insets and set proper padding
                    contentView.setOnApplyWindowInsetsListener((v, insets) -> {
                        try {
                            // Get status bar height from insets
                            int statusBarHeight = insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                            
                            // Set padding to move content below status bar
                            v.setPadding(0, statusBarHeight, 0, 0);
                            
                            XposedBridge.log(TAG + ": Applied status bar padding: " + statusBarHeight + "px");
                        } catch (Exception e) {
                            XposedBridge.log(TAG + ": Failed to apply insets padding - " + e.getMessage());
                        }
                        return insets; // Return insets for child views
                    });
                    
                    // Request insets application
                    contentView.requestApplyInsets();
                }
                
                XposedBridge.log(TAG + ": Enabled WindowInsets handling for content");
            } else {
                // For older versions, use traditional fitsSystemWindows
                View contentView = decorView.findViewById(android.R.id.content);
                if (contentView != null) {
                    contentView.setFitsSystemWindows(true);
                    contentView.requestApplyInsets();
                }
                
                XposedBridge.log(TAG + ": Applied fitsSystemWindows for content");
            }
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to force content view fitsSystemWindows - " + e.getMessage());
        }
    }
}