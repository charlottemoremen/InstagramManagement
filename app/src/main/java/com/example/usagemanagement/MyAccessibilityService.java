package com.example.usagemanagement;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class MyAccessibilityService extends AccessibilityService {

    private static final String TAG = "MyAccessibilityService";
    private static final String INSTAGRAM_PACKAGE_NAME = "com.instagram.android";
    private static MyAccessibilityService instance;
    private static AccessibilityServiceConnectionListener listener;

    private String currentActivePackageName = null;
    public boolean isGrayscaleEnabled = false;
    private View grayscaleOverlayView;
    private boolean isOverlayApplied = false;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static MyAccessibilityService getInstance() {
        return instance;
    }

    public static void setAccessibilityServiceConnectionListener(AccessibilityServiceConnectionListener connectionListener) {
        listener = connectionListener;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String packageName = event.getPackageName().toString();
        currentActivePackageName = packageName;

        // Log current active package for debugging
        Log.d(TAG, "Current active package: " + currentActivePackageName);

        if (INSTAGRAM_PACKAGE_NAME.equals(packageName) && isGrayscaleEnabled && !isOverlayApplied) {
            applyGrayscaleOverlay();
        }
    }

    public void setGrayscaleEnabled(boolean enabled) {
        isGrayscaleEnabled = enabled;
        if (!enabled) {
            removeGrayscaleOverlay();
        }
    }

    public boolean isAppCurrentlyActive(String packageName) {
        return packageName.equals(currentActivePackageName);
    }

    private void applyGrayscaleOverlay() {
        if (!isOverlayApplied) {
            grayscaleOverlayView = new View(this);
            grayscaleOverlayView.setBackgroundColor(0x998A8FA0); // Semi-transparent grey

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
            );

            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            windowManager.addView(grayscaleOverlayView, params);
            isOverlayApplied = true;

            Log.d(TAG, "Grayscale overlay applied.");
        }
    }

    public void removeGrayscaleOverlay() {
        if (isOverlayApplied && grayscaleOverlayView != null) {
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            windowManager.removeView(grayscaleOverlayView);
            grayscaleOverlayView = null;
            isOverlayApplied = false;

            Log.d(TAG, "Grayscale overlay removed.");
        }
    }



    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility Service Connected.");

        if (listener != null) {
            listener.onAccessibilityServiceConnected();
        }
    }

    public interface AccessibilityServiceConnectionListener {
        void onAccessibilityServiceConnected();
    }
}
