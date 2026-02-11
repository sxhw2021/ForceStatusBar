package com.example.forcestatusbar.hook;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.DisplayMetrics;
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
        
        // Android 11+ use WindowInsetsController
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hookWindowInsetsControllerImpl(lpparam);
        }
        
        XposedBridge.log(TAG + ": Initialized - " + lpparam.packageName);
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
        
        // Hook Activity.onCreate to set up from the beginning
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
     * Simplified approach: adjust root layout padding to avoid status bar overlap
     * Focus on minimizing touch offset while maintaining functionality
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
                
                // Simple adjustment: only add top padding for portrait, minimal for landscape
                boolean isLandscape = isLandscapeOrientation(activity);
                int topPadding = isLandscape ? Math.min(statusBarHeight / 4, 8) : statusBarHeight;
                
                // Apply adjusted padding
                rootView.setPadding(
                    originalPadding[0],
                    originalPadding[1] + topPadding,
                    originalPadding[2],
                    originalPadding[3]
                );
                
                XposedBridge.log(TAG + ": Applied padding adjustment - Landscape: " + isLandscape + 
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
            
            // Get the root view
            View rootView = contentView.getChildAt(0);
            if (rootView == null) {
                return;
            }
            
            // Get current orientation and status bar position
            int statusBarHeight = getStatusBarHeight(activity);
            boolean isLandscape = isLandscapeOrientation(activity);
            
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
                
                // Calculate padding adjustments based on orientation and status bar position
                PaddingAdjustment adjustment = calculatePaddingAdjustment(activity, statusBarHeight, isLandscape);
                
                // Apply smart padding adjustment
                rootView.setPadding(
                    originalPadding[0] + adjustment.left,
                    originalPadding[1] + adjustment.top,
                    originalPadding[2] + adjustment.right,
                    originalPadding[3] + adjustment.bottom
                );
                
                XposedBridge.log(TAG + ": Applied smart padding adjustment - Landscape: " + isLandscape + 
                          ", L:" + adjustment.left + ", T:" + adjustment.top + 
                          ", R:" + adjustment.right + ", B:" + adjustment.bottom + "px");
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
     * Calculate padding adjustments based on screen orientation
     * Simplified approach to avoid complex calculations
     */
    private PaddingAdjustment calculatePaddingAdjustment(Activity activity, int statusBarHeight, boolean isLandscape) {
        PaddingAdjustment adjustment = new PaddingAdjustment();
        
        if (isLandscape) {
            // For landscape, apply minimal padding to reduce touch offset
            // Some games move status bar to right in landscape
            adjustment.top = Math.min(statusBarHeight / 3, 6); // Small top padding
            adjustment.right = statusBarHeight / 4; // Small right padding for right-side status bar
            adjustment.left = statusBarHeight / 6; // Small left padding as backup
            
        } else {
            // For portrait, status bar is on top
            adjustment.top = statusBarHeight;
        }
        
        return adjustment;
    }
            
            // Small top padding to avoid any overlap
            adjustment.top = Math.min(statusBarHeight / 4, 8);
            
        } else {
            // Portrait mode: status bar is always on top
            adjustment.top = statusBarHeight;
        }
        
        return adjustment;
    }
    
    /**
     * Check if navigation bar is on the left side (some landscape layouts)
     */
    private boolean isNavigationBarOnLeft(Activity activity) {
        try {
            // Simple heuristic: check if device uses left-side navigation in landscape
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Set up orientation change listener for dynamic adjustment
     */
    private void setupOrientationChangeListener(final Activity activity) {
        try {
            // Override onConfigurationChanged for dynamic adjustment
            XposedHelpers.findAndHookMethod(
                Activity.class.getName(),
                activity.getClassLoader(),
                "onConfigurationChanged",
                Configuration.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Configuration newConfig = (Configuration) param.args[0];
                        
                        try {
                            // Get current orientation
                            int newOrientation = newConfig.orientation;
                            int oldOrientation = activity.getResources().getConfiguration().orientation;
                            
                            if (oldOrientation != newOrientation) {
                                XposedBridge.log(TAG + ": Orientation changed from " + oldOrientation + " to " + newOrientation + ", readjusting padding");
                                // Re-adjust padding for new orientation
                                adjustRootViewPadding(activity);
                            }
                        } catch (Exception e) {
                            XposedBridge.log(TAG + ": Error in orientation change handler - " + e.getMessage());
                        }
                    }
                }
            );
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to setup orientation listener - " + e.getMessage());
        }
    }
    
    /**
     * Class to hold padding adjustment values
     */
    private static class PaddingAdjustment {
        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;
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