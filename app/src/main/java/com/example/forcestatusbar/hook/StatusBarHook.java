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
        
        // NEW: Hook Display APIs to "lie" about screen size
        hookDisplayMetrics(lpparam);
        hookDisplaySize(lpparam);
        
        // Android 11+ use WindowInsetsController
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hookWindowInsetsControllerImpl(lpparam);
        }
        
        XposedBridge.log(TAG + ": Initialized with DisplayMetrics deception - " + lpparam.packageName);
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
                            XposedBridge.log(TAG + ": Intercepted setFlags - " + lpparam.packageName);
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
                                XposedBridge.log(TAG + ": Intercepted setAttributes - " + lpparam.packageName);
                            }
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to hook setAttributes - " + e.getMessage());
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
                        
                        // Clear fullscreen flags
                        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                        
                        // Force show status bar
                        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                        
                        // Enable Edge-to-Edge layout (Android 11+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            window.setDecorFitsSystemWindows(false);
                        }
                        
                        // Adjust root view padding to avoid status bar overlap
                        adjustRootViewPadding(activity);
                        
                        // Set system UI visibility
                        View decorView = window.getDecorView();
                        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                        
                        XposedBridge.log(TAG + ": Activity.onResume forced status bar - " + lpparam.packageName);
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
                        
                        // Force add FLAG_FORCE_NOT_FULLSCREEN
                        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                        
                        // Android 11+ enable Edge-to-Edge
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            window.setDecorFitsSystemWindows(false);
                        }
                        
                        XposedBridge.log(TAG + ": Activity.onCreate set status bar flags - " + lpparam.packageName);
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
                                XposedBridge.log(TAG + ": Intercepted WindowInsetsControllerImpl.hide(status bar) - " + lpparam.packageName);
                            }
                        }
                    });
                    
                    XposedBridge.log(TAG + ": Successfully hooked WindowInsetsController implementation - " + className);
                    break; // Exit after success
                }
            } catch (Exception e) {
                // Try next class
            }
        }
    }
    
    /**
     * NEW: Simplified padding adjustment thanks to DisplayMetrics deception
     * Apps will think screen is smaller, so minimal padding needed
     */
    private void adjustRootViewPadding(Activity activity) {
        try {
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            android.view.ViewGroup contentView = (android.view.ViewGroup) decorView.findViewById(android.R.id.content);
            
            if (contentView == null || contentView.getChildCount() == 0) {
                return;
            }
            
            // Get root view
            View rootView = contentView.getChildAt(0);
            if (rootView == null) {
                return;
            }
            
            // Get status bar height
            int statusBarHeight = getStatusBarHeight(activity);
            
            if (statusBarHeight > 0) {
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
                
                // NEW: Thanks to DisplayMetrics deception, apps will automatically leave space
                // Only add minimal safety padding to prevent edge cases
                boolean isLandscape = isLandscapeOrientation(activity);
                int topPadding = isLandscape ? Math.min(statusBarHeight / 8, 4) : Math.min(statusBarHeight / 4, 8);
                
                // Apply minimal adjusted padding
                rootView.setPadding(
                    originalPadding[0],
                    originalPadding[1] + topPadding,
                    originalPadding[2],
                    originalPadding[3]
                );
                
                XposedBridge.log(TAG + ": NEW: Applied minimal padding (thanks to DisplayMetrics deception) - Landscape: " + isLandscape + 
                          ", Top padding: " + topPadding + "px");
            }
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to adjust padding - " + e.getMessage());
        }
    }
    
    /**
     * Check if device is in landscape orientation
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
     * Hook DisplayMetrics APIs to "lie" about screen size
     * Core idea: Let apps think screen height = physical height - status bar height
     */
    private void hookDisplayMetrics(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Display.getMetrics() - API 1+
            XposedHelpers.findAndHookMethod(
                "android.view.Display",
                lpparam.classLoader,
                "getMetrics",
                android.graphics.DisplayMetrics.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.graphics.DisplayMetrics metrics = (android.graphics.DisplayMetrics) param.args[0];
                        if (metrics == null) return;
                        
                        android.view.Display display = (android.view.Display) param.thisObject;
                        
                        try {
                            // Get real physical screen size
                            android.graphics.Point realSize = new android.graphics.Point();
                            display.getRealSize(realSize);
                            
                            // Get status bar height
                            int statusBarHeight = getStatusBarHeightForDisplay(display);
                            
                            // Core deception: modify heightPixels
                            int originalHeight = metrics.heightPixels;
                            metrics.heightPixels = realSize.y - statusBarHeight;
                            
                            // Also modify noncompatHeightPixels if available
                            try {
                                java.lang.reflect.Field field = android.graphics.DisplayMetrics.class.getDeclaredField("noncompatHeightPixels");
                                field.setAccessible(true);
                                field.set(metrics, metrics.heightPixels);
                            } catch (Exception ignored) {}
                            
                            XposedBridge.log(TAG + ": Display.getMetrics() - Original: " + originalHeight + 
                                          ", Modified: " + metrics.heightPixels + 
                                          ", StatusBar: " + statusBarHeight + "px");
                        } catch (Exception e) {
                            XposedBridge.log(TAG + ": Failed to modify Display.getMetrics() - " + e.getMessage());
                        }
                    }
                }
            );
            
            // Hook Display.getRealMetrics() - API 17+
            XposedHelpers.findAndHookMethod(
                "android.view.Display",
                lpparam.classLoader,
                "getRealMetrics",
                android.graphics.DisplayMetrics.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Apply same deception as getMetrics()
                        android.graphics.DisplayMetrics metrics = (android.graphics.DisplayMetrics) param.args[0];
                        if (metrics == null) return;
                        
                        android.view.Display display = (android.view.Display) param.thisObject;
                        
                        try {
                            android.graphics.Point realSize = new android.graphics.Point();
                            display.getRealSize(realSize);
                            
                            int statusBarHeight = getStatusBarHeightForDisplay(display);
                            int originalHeight = metrics.heightPixels;
                            metrics.heightPixels = realSize.y - statusBarHeight;
                            
                            // Also modify noncompatHeightPixels if available
                            try {
                                java.lang.reflect.Field field = android.graphics.DisplayMetrics.class.getDeclaredField("noncompatHeightPixels");
                                field.setAccessible(true);
                                field.set(metrics, metrics.heightPixels);
                            } catch (Exception ignored) {}
                            
                            XposedBridge.log(TAG + ": Display.getRealMetrics() - Original: " + originalHeight + 
                                          ", Modified: " + metrics.heightPixels + 
                                          ", StatusBar: " + statusBarHeight + "px");
                        } catch (Exception e) {
                            XposedBridge.log(TAG + ": Failed to modify Display.getRealMetrics() - " + e.getMessage());
                        }
                    }
                }
            );
            
            XposedBridge.log(TAG + ": DisplayMetrics hooks installed successfully");
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to install DisplayMetrics hooks - " + e.getMessage());
        }
    }
    
    /**
     * Hook Display.getSize() and getRealSize() methods
     */
    private void hookDisplaySize(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Display.getSize() - API 13+
            XposedHelpers.findAndHookMethod(
                "android.view.Display",
                lpparam.classLoader,
                "getSize",
                android.graphics.Point.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.graphics.Point size = (android.graphics.Point) param.args[0];
                        if (size == null) return;
                        
                        android.view.Display display = (android.view.Display) param.thisObject;
                        
                        try {
                            // Get real size first
                            android.graphics.Point realSize = new android.graphics.Point();
                            display.getRealSize(realSize);
                            
                            // Get status bar height
                            int statusBarHeight = getStatusBarHeightForDisplay(display);
                            
                            // Modify size
                            int originalY = size.y;
                            size.y = realSize.y - statusBarHeight;
                            
                            XposedBridge.log(TAG + ": Display.getSize() - Original Y: " + originalY + 
                                          ", Modified Y: " + size.y + 
                                          ", StatusBar: " + statusBarHeight + "px");
                        } catch (Exception e) {
                            XposedBridge.log(TAG + ": Failed to modify Display.getSize() - " + e.getMessage());
                        }
                    }
                }
            );
            
            // Hook Display.getRealSize() - API 17+
            XposedHelpers.findAndHookMethod(
                "android.view.Display",
                lpparam.classLoader,
                "getRealSize",
                android.graphics.Point.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.graphics.Point size = (android.graphics.Point) param.args[0];
                        if (size == null) return;
                        
                        android.view.Display display = (android.view.Display) param.thisObject;
                        
                        try {
                            // Get real size
                            android.graphics.Point realSize = new android.graphics.Point();
                            display.getRealSize(realSize);
                            
                            // Get status bar height
                            int statusBarHeight = getStatusBarHeightForDisplay(display);
                            
                            // Modify real size to match our deception
                            int originalY = size.y;
                            size.y = realSize.y - statusBarHeight;
                            
                            XposedBridge.log(TAG + ": Display.getRealSize() - Original Y: " + originalY + 
                                          ", Modified Y: " + size.y + 
                                          ", StatusBar: " + statusBarHeight + "px");
                        } catch (Exception e) {
                            XposedBridge.log(TAG + ": Failed to modify Display.getRealSize() - " + e.getMessage());
                        }
                    }
                }
            );
            
            XposedBridge.log(TAG + ": Display size hooks installed successfully");
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to install Display size hooks - " + e.getMessage());
        }
    }
    
    /**
     * Get status bar height for Display object using multiple fallback methods
     */
    private int getStatusBarHeightForDisplay(android.view.Display display) {
        try {
            // Try to get context from Display
            Object windowManager = XposedHelpers.callMethod(display, "getWindowManager");
            if (windowManager != null) {
                Context context = (Context) XposedHelpers.callMethod(windowManager, "getContext");
                if (context != null) {
                    return getStatusBarHeight(context);
                }
            }
        } catch (Exception e) {
            // Fallback to global context method
        }
        
        // Fallback: try to get ActivityThread context
        try {
            Object activityThread = XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", null), "currentActivityThread");
            Context context = (Context) XposedHelpers.callMethod(activityThread, "getSystemContext");
            if (context != null) {
                return getStatusBarHeight(context);
            }
        } catch (Exception e) {
            // Final fallback
        }
        
        // Ultimate fallback: use 24dp * density
        try {
            android.graphics.DisplayMetrics metrics = new android.graphics.DisplayMetrics();
            display.getMetrics(metrics);
            return (int) (24 * metrics.density + 0.5f);
        } catch (Exception e) {
            return 48; // Hardcoded fallback
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