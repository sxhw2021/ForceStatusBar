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

public class StatusBarHookBackup implements IXposedHookLoadPackage {
    
    private static final String TAG = "ForceStatusBar";
    
    // Recursion prevention for DisplayMetrics hooks
    private static volatile boolean isInDisplayMetricsHook = false;
    
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookWindowMethods(lpparam);
        hookDecorView(lpparam);
        hookActivityLifecycle(lpparam);
        
        // LIMITED: Hook Display APIs but exclude getRealSize() to avoid stack overflow
        hookDisplayMetricsSafe(lpparam);
        hookDisplaySizeSafe(lpparam);
        
        // Android 11+ use WindowInsetsController
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hookWindowInsetsControllerImpl(lpparam);
        }
        
        XposedBridge.log(TAG + ": Backup version initialized (no getRealSize hook) - " + lpparam.packageName);
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
                        
                        // Clear fullscreen flags
                        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                        window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                        
                        // For Android 11+ use WindowInsetsController
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                Object controller = window.getInsetsController();
                                if (controller != null) {
                                    XposedHelpers.callMethod(controller, "show", 
                                        Class.forName("android.view.WindowInsets$Type").getField("statusBars").get(null));
                                }
                            } catch (Exception e) {
                                XposedBridge.log(TAG + ": Failed to show status bar using WindowInsetsController - " + e.getMessage());
                            }
                        } else {
                            // For Android 10 and below
                            View decorView = window.getDecorView();
                            int uiOptions = View.SYSTEM_UI_FLAG_VISIBLE;
                            decorView.setSystemUiVisibility(uiOptions);
                        }
                        
                        XposedBridge.log(TAG + ": Force showed status bar in onResume - " + lpparam.packageName);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to hook Activity.onResume - " + e.getMessage());
        }
    }
    
    private void hookWindowInsetsControllerImpl(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook WindowInsetsControllerImpl.hide()
            XposedHelpers.findAndHookMethod(
                "android.view.WindowInsetsControllerImpl",
                lpparam.classLoader,
                "hide",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        int type = (int) param.args[0];
                        
                        // If trying to hide status bar, block it
                        if ((type & 1) != 0) { // WindowInsets.Type.STATUS_BARS = 1
                            param.setResult(null);
                            XposedBridge.log(TAG + ": Blocked WindowInsetsController.hide(statusBars) - " + lpparam.packageName);
                        }
                    }
                }
            );
            
            // Hook WindowInsetsControllerImpl.show() to ensure status bar is always visible
            XposedHelpers.findAndHookMethod(
                "android.view.WindowInsetsControllerImpl",
                lpparam.classLoader,
                "show",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        int type = (int) param.args[0];
                        
                        // Always include status bar in show
                        if ((type & 1) == 0) { // If not already showing status bar
                            param.args[0] = type | 1; // Add status bar type
                            XposedBridge.log(TAG + ": Forced status bar show in WindowInsetsController.show() - " + lpparam.packageName);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to hook WindowInsetsControllerImpl - " + e.getMessage());
        }
    }
    
    /**
     * SAFE version of DisplayMetrics hooks - EXCLUDES getRealSize() to avoid stack overflow
     */
    private void hookDisplayMetricsSafe(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Display.getMetrics() - API 1+ with recursion prevention
            XposedHelpers.findAndHookMethod(
                "android.view.Display",
                lpparam.classLoader,
                "getMetrics",
                DisplayMetrics.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Recursion prevention check
                        if (isInDisplayMetricsHook) {
                            return;  // Skip if we're already in a DisplayMetrics hook
                        }
                        
                        DisplayMetrics metrics = (DisplayMetrics) param.args[0];
                        if (metrics == null) return;
                        
                        Display display = (Display) param.thisObject;
                        if (display == null) return;
                        
                        try {
                            isInDisplayMetricsHook = true;
                            // Get real physical screen size
                            Point realSize = new Point();
                            
                            if (realSize != null && realSize.x > 0 && realSize.y > 0) {
                                // Get status bar height
                                int statusBarHeight = getStatusBarHeightForDisplay(display);
                                
                                // Core deception: modify heightPixels (with safety checks)
                                int originalHeight = metrics.heightPixels;
                                int modifiedHeight = realSize.y - statusBarHeight;
                                
                                // Safety check: don't make height negative
                                if (modifiedHeight < 0) {
                                    modifiedHeight = originalHeight; // Revert to original
                                }
                                
                                // Safety check: reasonable limits (can't reduce height by more than 50%)
                                int maxReduction = originalHeight / 2;
                                if (modifiedHeight < originalHeight - maxReduction) {
                                    modifiedHeight = originalHeight - maxReduction;
                                }
                                
                                // Apply the modification
                                metrics.heightPixels = modifiedHeight;
                                
                                XposedBridge.log(TAG + ": BACKUP Display.getMetrics() - SAFE: " + originalHeight + 
                                              ", Modified: " + modifiedHeight + 
                                              ", StatusBar: " + statusBarHeight + "px");
                            }
                        } catch (Exception e) {
                            XposedBridge.log(TAG + ": BACKUP Display.getMetrics() - Exception: " + e.getMessage());
                        } finally {
                            isInDisplayMetricsHook = false;
                        }
                    }
                }
            );
            
            // Hook Display.getRealMetrics() - API 17+ with recursion prevention
            XposedHelpers.findAndHookMethod(
                "android.view.Display",
                lpparam.classLoader,
                "getRealMetrics",
                DisplayMetrics.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Recursion prevention check
                        if (isInDisplayMetricsHook) {
                            return;  // Skip if we're already in a DisplayMetrics hook
                        }
                        
                        DisplayMetrics metrics = (DisplayMetrics) param.args[0];
                        if (metrics == null) return;
                        
                        Display display = (Display) param.thisObject;
                        if (display == null) return;
                        
                        try {
                            isInDisplayMetricsHook = true;
                            Point realSize = new Point();
                            
                            if (realSize != null && realSize.x > 0 && realSize.y > 0) {
                                int statusBarHeight = getStatusBarHeightForDisplay(display);
                                int originalHeight = metrics.heightPixels;
                                int modifiedHeight = realSize.y - statusBarHeight;
                                
                                // Safety checks
                                if (modifiedHeight < 0) {
                                    modifiedHeight = originalHeight;
                                } else if (modifiedHeight < originalHeight / 2) {
                                    modifiedHeight = originalHeight / 2;
                                }
                                
                                metrics.heightPixels = modifiedHeight;
                                
                                XposedBridge.log(TAG + ": BACKUP Display.getRealMetrics() - SAFE: " + originalHeight + 
                                              ", Modified: " + modifiedHeight + 
                                              ", StatusBar: " + statusBarHeight + "px");
                            }
                        } catch (Exception e) {
                            XposedBridge.log(TAG + ": BACKUP Display.getRealMetrics() - Exception: " + e.getMessage());
                        } finally {
                            isInDisplayMetricsHook = false;
                        }
                    }
                }
            );
            
            XposedBridge.log(TAG + ": Backup DisplayMetrics hooks installed (no getRealSize)");
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to install backup DisplayMetrics hooks - " + e.getMessage());
        }
    }
    
    /**
     * SAFE version of Display size hooks - EXCLUDES getRealSize() to avoid stack overflow
     */
    private void hookDisplaySizeSafe(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Display.getSize() - API 13+ with recursion prevention
            XposedHelpers.findAndHookMethod(
                "android.view.Display",
                lpparam.classLoader,
                "getSize",
                Point.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // Recursion prevention check
                        if (isInDisplayMetricsHook) {
                            return;  // Skip if we're already in a DisplayMetrics hook
                        }
                        
                        Point size = (Point) param.args[0];
                        if (size == null) return;
                        
                        Display display = (Display) param.thisObject;
                        
                        try {
                            isInDisplayMetricsHook = true;
                            // Get real size first
                            Point realSize = new Point();
                            display.getRealSize(realSize);
                            
                            // Get status bar height
                            int statusBarHeight = getStatusBarHeightForDisplay(display);
                            
                            // Modify size
                            int originalY = size.y;
                            size.y = realSize.y - statusBarHeight;
                            
                            XposedBridge.log(TAG + ": BACKUP Display.getSize() - Original Y: " + originalY + 
                                          ", Modified Y: " + size.y + 
                                          ", StatusBar: " + statusBarHeight + "px");
                        } catch (Exception e) {
                            XposedBridge.log(TAG + ": BACKUP Failed to modify Display.getSize() - " + e.getMessage());
                        } finally {
                            isInDisplayMetricsHook = false;
                        }
                    }
                }
            );
            
            XposedBridge.log(TAG + ": Backup DisplaySize hooks installed (no getRealSize)");
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to install backup DisplaySize hooks - " + e.getMessage());
        }
    }
    
    private int getStatusBarHeightForDisplay(Display display) {
        try {
            // Try to get status bar height from system resources
            Context context = (Context) XposedHelpers.callMethod(
                XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null), 
                    "currentApplication"
                ), 
                "getApplicationContext"
            );
            
            int resourceId = context.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
            
            if (resourceId > 0) {
                return context.getResources().getDimensionPixelSize(resourceId);
            }
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to get status bar height: " + e.getMessage());
        }
        
        // Fallback to typical status bar height
        return 24; // dp, typical for most devices
    }
}