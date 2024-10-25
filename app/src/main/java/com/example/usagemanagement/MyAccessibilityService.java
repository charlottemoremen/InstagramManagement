package com.example.usagemanagement;

import android.accessibilityservice.AccessibilityService;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class MyAccessibilityService extends AccessibilityService {

    private static MyAccessibilityService instance;
    private static final String TAG = "AccessibilityLog";
    private static final String INSTAGRAM_PACKAGE_NAME = "com.instagram.android";
    private long manualInstagramUsage = 0;
    private long initialInstagramUsage = 0; // Store the starting usage from UsageStats
    private long lastInteractionTime = 0;
    private long lastForegroundTime = 0;
    private boolean isInstagramForeground = false;
    private static final long GRAYSCALE_LIMIT = 5 * 60 * 1000; // 5 minutes limit in milliseconds
    public static final long REDIRECT_LIMIT = 10 * 60 * 1000; // 10 minutes limit in milliseconds
    private static final long GRACE_PERIOD_MS = 5000; // 5 seconds grace period for no interaction
    private Handler handler = new Handler();
    private View grayscaleOverlayView;
    private boolean isOverlayApplied = false;

    public static MyAccessibilityService getServiceInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility service connected.");
        initialInstagramUsage = getRealInstagramTime(this); // Start by fetching today's usage
        Log.i(TAG, "Initial Instagram usage from system: " + initialInstagramUsage + " ms.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() != null && event.getPackageName().equals(INSTAGRAM_PACKAGE_NAME)) {
            int eventType = event.getEventType();

            // Track Instagram in the foreground
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

                if (!isInstagramForeground) {
                    isInstagramForeground = true;
                    lastForegroundTime = System.currentTimeMillis();
                    Log.i(TAG, "Instagram detected in foreground.");
                }

                lastInteractionTime = System.currentTimeMillis();
                trackInstagramUsage();
            }
        }

        // Grace period for detecting Instagram moved to background
        if (isInstagramForeground && (System.currentTimeMillis() - lastInteractionTime > GRACE_PERIOD_MS)) {
            Log.i(TAG, "No interaction detected for " + GRACE_PERIOD_MS + "ms. Assuming Instagram is in the background.");
            isInstagramForeground = false;
            trackInstagramBackground();  // Track the time when it moves to the background
            validateUsageWithStats();  // Re-validate against UsageStats
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted.");
    }

    // Tracks Instagram usage while it is in the foreground
    private void trackInstagramUsage() {
        long currentTime = System.currentTimeMillis();
        long usageTime = currentTime - lastForegroundTime;
        manualInstagramUsage += usageTime;
        lastForegroundTime = currentTime;
        Log.i(TAG, "Tracking Instagram usage. Total manually tracked: " + manualInstagramUsage + " ms.");

        long totalUsageToday = manualInstagramUsage + initialInstagramUsage;

        // Apply grayscale if usage crosses the grayscale limit
        if (totalUsageToday >= GRAYSCALE_LIMIT && !isOverlayApplied) {
            applyGrayscaleOverlay();
        }

        // Redirect after total usage crosses the redirect limit
        if (totalUsageToday >= REDIRECT_LIMIT) {
            redirectToApp();
        }
    }

    // Tracks when Instagram is moved to the background
    private void trackInstagramBackground() {
        long currentTime = System.currentTimeMillis();
        long usageTime = currentTime - lastForegroundTime;
        manualInstagramUsage += usageTime;
        Log.i(TAG, "Instagram moved to background. Added " + usageTime + " ms to usage.");
        removeGrayscaleOverlay(); // Remove grayscale overlay when app is in the background
    }

    // Fetch usage data from UsageStatsManager and validate total tracked time
    private void validateUsageWithStats() {
        long currentSystemUsage = getRealInstagramTime(this);
        long totalTrackedTime = initialInstagramUsage + manualInstagramUsage;

        if (currentSystemUsage > totalTrackedTime) {
            Log.i(TAG, "Updating total usage time with system data.");
            manualInstagramUsage = currentSystemUsage - initialInstagramUsage;
        }

        Log.i(TAG, "Current Instagram usage validated: " + currentSystemUsage + " ms.");
    }

    // Fetches the real Instagram time from UsageStatsManager
    public long getRealInstagramTime(Context context) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long startTime = getMidnightTimeMillis();  // Get the time from midnight today
        long endTime = System.currentTimeMillis();

        long realUsageTime = calculateInstagramUsage(usageStatsManager, startTime, endTime);
        Log.i(TAG, "Real Instagram usage time from system: " + realUsageTime + " ms.");
        return realUsageTime;
    }

    // Helper method to calculate Instagram usage from UsageStats
    private long calculateInstagramUsage(UsageStatsManager usageStatsManager, long startTime, long endTime) {
        long totalUsageTime = 0;
        UsageEvents events = usageStatsManager.queryEvents(startTime, endTime);
        UsageEvents.Event event = new UsageEvents.Event();
        long lastForegroundTime = 0;

        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (INSTAGRAM_PACKAGE_NAME.equals(event.getPackageName())) {
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastForegroundTime = event.getTimeStamp();
                } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED && lastForegroundTime != 0) {
                    totalUsageTime += (event.getTimeStamp() - lastForegroundTime);
                    lastForegroundTime = 0;
                }
            }
        }

        return totalUsageTime;
    }

    // Returns the time at midnight of the current day
    private long getMidnightTimeMillis() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    // Applies the grayscale overlay
    private void applyGrayscaleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot apply grayscale overlay: Missing SYSTEM_ALERT_WINDOW permission.");
            return;
        }

        if (!isOverlayApplied) {
            grayscaleOverlayView = new View(this);
            grayscaleOverlayView.setBackgroundColor(0x7F000000); // Semi-transparent gray overlay

            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );

            windowManager.addView(grayscaleOverlayView, params);
            isOverlayApplied = true;
            Log.i(TAG, "Grayscale overlay applied.");
        }
    }

    // Removes the grayscale overlay
    private void removeGrayscaleOverlay() {
        if (isOverlayApplied) {
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (grayscaleOverlayView != null) {
                windowManager.removeView(grayscaleOverlayView);
                grayscaleOverlayView = null;
            }
            isOverlayApplied = false;
            Log.i(TAG, "Grayscale overlay removed.");
        }
    }

    // Redirects the user to the app after exceeding the usage limit
    private void redirectToApp() {
        Intent intent = new Intent(this, RedirectActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        Log.i(TAG, "Redirecting user to app after 10 minutes.");
    }

    // Public method to get real Instagram usage time (combines initial + manual tracking)
    public static long getRealInstagramUsageTime(Context context) {
        if (instance == null) {
            Log.w(TAG, "Service instance is null.");
            return 0;
        }
        return instance.getRealInstagramTime(context) + instance.manualInstagramUsage;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null); // Stop all handler callbacks
        removeGrayscaleOverlay(); // Ensure the grayscale overlay is removed
    }
}
