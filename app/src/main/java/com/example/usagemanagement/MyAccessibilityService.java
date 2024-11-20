package com.example.usagemanagement;

import android.accessibilityservice.AccessibilityService;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;

public class MyAccessibilityService extends AccessibilityService {

    private static final String TAG = "MyAccessibilityService";
    private static MyAccessibilityService instance;
    private static AccessibilityServiceConnectionListener listener;
    private String currentActivePackageName = null;
    private static final long RECENT_ACTIVITY_THRESHOLD = 5000; // 5 seconds
    private long lastInstagramActiveTime = 0;
    private static final String INSTAGRAM_PACKAGE_NAME = "com.instagram.android";

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
        if (event != null && event.getPackageName() != null) {
            String currentPackageName = event.getPackageName().toString();

            // Update the current active package
            currentActivePackageName = currentPackageName;

            if (INSTAGRAM_PACKAGE_NAME.equals(currentPackageName)) {
                lastInstagramActiveTime = System.currentTimeMillis();
            }

            Log.d(TAG, "Current active package: " + currentPackageName);
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted.");
    }

    public boolean isAppCurrentlyActive(String packageName) {
        return packageName.equals(currentActivePackageName);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility Service Connected.");

        // Notify the listener (MainActivity)
        if (listener != null) {
            listener.onAccessibilityServiceConnected();
        }
    }

    // Ensure the interface is public so it can be implemented in MainActivity
    public interface AccessibilityServiceConnectionListener {
        void onAccessibilityServiceConnected();
    }
}
