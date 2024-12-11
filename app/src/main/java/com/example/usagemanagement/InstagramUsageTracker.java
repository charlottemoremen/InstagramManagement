package com.example.usagemanagement;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.Calendar;
import java.util.List;

public class InstagramUsageTracker {

    private static final String TAG = "InstagramUsageTracker";
    private static final String INSTAGRAM_PACKAGE_NAME = "com.instagram.android";
    private static final long CHECK_INTERVAL_MS = 500; // Check every half second
    private static final long TIMEKEEPING_ERROR_THRESHOLD_MS = 5000; // 5 seconds

    private final Context context;
    private final Handler handler;

    private long IGSessionStartTime = 0;
    private long lastIGUsageStats = 0;
    private long sessionTime = 0;

    private boolean isInstagramActive = false;
    private InstagramUsageListener usageListener;

    public InstagramUsageTracker(Context context) {
        this.context = context;
        this.handler = new Handler();
    }

    public void setInstagramUsageListener(InstagramUsageListener listener) {
        this.usageListener = listener;
    }

    public void startTracking() {
        // Initialize the last known usage stats value
        lastIGUsageStats = getInstagramUsageToday();
        handler.postDelayed(this::trackInstagramUsage, CHECK_INTERVAL_MS);
    }

    private boolean hasInstagramUsageChanged(long currentUsageStatsTime) {
        boolean hasChanged = currentUsageStatsTime > lastIGUsageStats;
        Log.d(TAG, "Has Instagram usage changed: " + hasChanged);
        return hasChanged;
    }

    private void trackInstagramUsage() {
        long currentUsageStatsTime = getInstagramUsageToday();
        MyAccessibilityService service = MyAccessibilityService.getInstance();

        Log.d(TAG, "Checking Instagram usage...");
        Log.d(TAG, "Current UsageStats time: " + currentUsageStatsTime);
        Log.d(TAG, "Last recorded UsageStats time: " + lastIGUsageStats);
        Log.d(TAG, "Is Instagram active: " + isInstagramActive);

        // Detect Instagram open using AccessibilityService
        if (!isInstagramActive && service != null && service.isAppCurrentlyActive(INSTAGRAM_PACKAGE_NAME)) {
            isInstagramActive = true;
            IGSessionStartTime = System.currentTimeMillis();
            Log.d(TAG, "Instagram detected as open at: " + IGSessionStartTime);
        }

        // Detect Instagram close using UsageStats
        if (isInstagramActive && hasInstagramUsageChanged(currentUsageStatsTime)) {
            isInstagramActive = false;
            logSessionDuration();
            resetSessionTracking();

            // Remove grayscale overlay
            if (service != null) {
                Log.d(TAG, "Removing grayscale overlay.");
                service.removeGrayscaleOverlay();
            }
        } else if (!isInstagramActive && service != null && !service.isAppCurrentlyActive(INSTAGRAM_PACKAGE_NAME)) {
            // Ensure grayscale is removed if no Instagram activity
            if (service.isGrayscaleEnabled) {
                Log.d(TAG, "Forcing grayscale removal as Instagram is inactive.");
                service.removeGrayscaleOverlay();
            }
        }

        // Notify listener for UI updates
        notifyInstagramUsage(currentUsageStatsTime);
        lastIGUsageStats = currentUsageStatsTime;

        handler.postDelayed(this::trackInstagramUsage, CHECK_INTERVAL_MS);
    }

    private void updateSessionTime() {
        long currentTime = System.currentTimeMillis();
        sessionTime = currentTime - IGSessionStartTime;
    }

    private void logSessionDuration() {
        if (IGSessionStartTime > 0) {
            long sessionEndTime = System.currentTimeMillis();
            long sessionDuration = sessionEndTime - IGSessionStartTime;

            // Calculate the discrepancy between manual tracking and system tracking
            long updatedIGUsageStats = getInstagramUsageToday();
            long trackedDurationChange = updatedIGUsageStats - lastIGUsageStats;
            long discrepancy = Math.abs(trackedDurationChange - sessionDuration);

            // Log session duration and discrepancy
            Log.d(TAG, "Instagram session ended. Duration: " + sessionDuration + " ms (" +
                    (sessionDuration / 1000) + " seconds)");
            Log.d(TAG, "Tracked usage time change: " + trackedDurationChange + " ms (" +
                    (trackedDurationChange / 1000) + " seconds)");
            Log.d(TAG, "Discrepancy between manual and tracked usage: " + discrepancy + " ms (" +
                    (discrepancy / 1000) + " seconds)");

            // Update the last known usage stats
            lastIGUsageStats = updatedIGUsageStats;
        }
    }

    private void validateAndResetTracking(long currentUsageStatsTime) {
        long estimatedTotalIGTime = lastIGUsageStats + sessionTime;
        if (Math.abs(currentUsageStatsTime - estimatedTotalIGTime) > TIMEKEEPING_ERROR_THRESHOLD_MS) {
            Log.w(TAG, "Timekeeping error detected. Discrepancy: "
                    + Math.abs(currentUsageStatsTime - estimatedTotalIGTime) + " ms");
        }
        resetSessionTracking(); // Ensure session tracking is reset
    }

    private void notifyInstagramUsage(long usageStatsTime) {
        if (usageListener != null) {
            usageListener.onInstagramUsageUpdated(usageStatsTime); // Notify only UsageStats time
        }
    }

    private void resetSessionTracking() {
        IGSessionStartTime = 0;
        sessionTime = 0;
        isInstagramActive = false;
        Log.d(TAG, "Instagram session tracking reset.");
    }

    private boolean isInstagramCurrentlyOpen() {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        return service != null && service.isAppCurrentlyActive(INSTAGRAM_PACKAGE_NAME);
    }

    public long getInstagramUsageToday() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            Log.w(TAG, "UsageStatsManager is null.");
            return 0;
        }

        Calendar calendar = Calendar.getInstance();
        long endTime = calendar.getTimeInMillis();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();

        List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

        if (usageStatsList == null) {
            Log.w(TAG, "UsageStatsManager returned null.");
            return 0;
        }

        for (UsageStats stats : usageStatsList) {
            if (INSTAGRAM_PACKAGE_NAME.equals(stats.getPackageName())) {
                Log.d(TAG, "Instagram total foreground time: " + stats.getTotalTimeInForeground());
                return stats.getTotalTimeInForeground();
            }
        }
        return 0;
    }
    public interface InstagramUsageListener {
        void onInstagramUsageUpdated(long estimatedUsageTime);
    }
}