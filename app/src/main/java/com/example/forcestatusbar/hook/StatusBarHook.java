package com.example.forcestatusbar.hook;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class StatusBarHook implements IXposedHookLoadPackage {
    
    private static final String TAG = "ForceStatusBar";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
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
                        
                        // Setup dynamic status bar color
                        setupDynamicStatusBarColor(activity);
                        
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
     * Improved approach: adjust root layout padding to avoid status bar overlap
     * Uses more precise edge detection to minimize touch offset
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
            
            // Get system insets for precise edge detection (Android 11+)
            int statusBarHeight = getStatusBarHeight(activity);
            int navigationBarHeight = 0;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsets insets = decorView.getRootWindowInsets();
                if (insets != null) {
                    statusBarHeight = insets.getStableInsetTop();
                    navigationBarHeight = insets.getStableInsetBottom();
                }
            }
            
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
                
                // Get current orientation
                boolean isLandscape = isLandscapeOrientation(activity);
                
                // More precise padding calculation
                int topPadding;
                if (isLandscape) {
                    // In landscape, status bar is usually on the right side (not top)
                    // Only add minimal padding to avoid touch offset
                    topPadding = Math.min(statusBarHeight / 6, 4);
                } else {
                    // In portrait, add exact status bar height
                    topPadding = statusBarHeight;
                }
                
                // Apply adjusted padding
                rootView.setPadding(
                    originalPadding[0],
                    originalPadding[1] + topPadding,
                    originalPadding[2],
                    originalPadding[3] + (isLandscape ? 0 : navigationBarHeight)
                );
                
                XposedBridge.log(TAG + ": Applied precise padding - Landscape: " + isLandscape + 
                          ", Top: " + topPadding + "px, Nav: " + navigationBarHeight + "px");
            }
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to adjust padding - " + e.getMessage());
        }
    }
    
    /**
     * Setup dynamic status bar color that adapts to game content
     */
    private void setupDynamicStatusBarColor(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        
        try {
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            
            // Set transparent status bar initially
            window.setStatusBarColor(Color.TRANSPARENT);
            
            // Add layout listener to detect content changes and adjust status bar color
            decorView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    try {
                        updateStatusBarColor(activity, window, decorView);
                    } catch (Exception e) {
                        // Ignore errors
                    }
                }
            });
            
            // Initial color update
            mainHandler.postDelayed(() -> {
                try {
                    updateStatusBarColor(activity, window, decorView);
                } catch (Exception e) {
                    // Ignore errors
                }
            }, 500);
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to setup dynamic status bar color - " + e.getMessage());
        }
    }
    
    /**
     * Update status bar color based on content behind it
     */
    private void updateStatusBarColor(Activity activity, Window window, View decorView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        
        try {
            // Get the content view
            android.view.ViewGroup contentView = decorView.findViewById(android.R.id.content);
            if (contentView == null || contentView.getChildCount() == 0) {
                return;
            }
            
            View rootView = contentView.getChildAt(0);
            if (rootView == null) {
                return;
            }
            
            // Get color from top of the screen
            int dominantColor = getDominantColorFromTop(rootView);
            
            // Calculate if color is light or dark
            boolean isLightColor = isLightColor(dominantColor);
            
            // Set status bar color with slight transparency
            int statusBarColor = adjustColorForStatusBar(dominantColor);
            window.setStatusBarColor(statusBarColor);
            
            // Set status bar text color (light or dark icons)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    if (isLightColor) {
                        controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        );
                    } else {
                        controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View systemUiView = window.getDecorView();
                int flags = systemUiView.getSystemUiVisibility();
                if (isLightColor) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                systemUiView.setSystemUiVisibility(flags);
            }
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed to update status bar color - " + e.getMessage());
        }
    }
    
    /**
     * Get dominant color from the top portion of the view
     */
    private int getDominantColorFromTop(View view) {
        try {
            // Try to get color from view background
            Drawable background = view.getBackground();
            if (background instanceof ColorDrawable) {
                return ((ColorDrawable) background).getColor();
            }
            
            // If no solid color, try to sample from top area
            // Use a simple heuristic: check if view has children and get their backgrounds
            if (view instanceof android.view.ViewGroup) {
                android.view.ViewGroup viewGroup = (android.view.ViewGroup) view;
                for (int i = 0; i < Math.min(viewGroup.getChildCount(), 3); i++) {
                    View child = viewGroup.getChildAt(i);
                    if (child != null && child.getBackground() instanceof ColorDrawable) {
                        return ((ColorDrawable) child.getBackground()).getColor();
                    }
                }
            }
            
            // Default: semi-transparent dark color
            return Color.parseColor("#80000000");
        } catch (Exception e) {
            return Color.parseColor("#80000000");
        }
    }
    
    /**
     * Check if a color is light or dark
     */
    private boolean isLightColor(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        
        // Calculate luminance using standard formula
        double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255;
        return luminance > 0.5;
    }
    
    /**
     * Adjust color for status bar with slight transparency
     */
    private int adjustColorForStatusBar(int color) {
        int alpha = Color.alpha(color);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        
        // Ensure minimum transparency for readability
        int newAlpha = Math.max(alpha, 200); // At least ~78% opaque
        
        return Color.argb(newAlpha, red, green, blue);
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