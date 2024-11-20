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
    private static final long CHECK_INTERVAL_MS = 5000; // Check every 5 seconds
    private static final long TIMEKEEPING_ERROR_THRESHOLD_MS = 15000; // 15 seconds

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

    private void trackInstagramUsage() {
        long currentUsageStatsTime = getInstagramUsageToday();
        boolean isCurrentlyOpen = isInstagramCurrentlyOpen();

        if (isCurrentlyOpen) {
            if (!isInstagramActive) {
                // Start a new session
                IGSessionStartTime = System.currentTimeMillis();
                isInstagramActive = true;
                Log.d(TAG, "Instagram session started at: " + IGSessionStartTime);
            }
            // Update session time
            updateSessionTime();
        } else if (isInstagramActive) {
            // Instagram was active but is now closed
            logSessionDuration();
            validateAndResetTracking(currentUsageStatsTime);
        }

        // Always notify listener with the current `UsageStats` value
        notifyInstagramUsage(currentUsageStatsTime);

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
        if (usageStatsManager == null) return 0;

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
                return stats.getTotalTimeInForeground();
            }
        }
        return 0;
    }

    public interface InstagramUsageListener {
        void onInstagramUsageUpdated(long estimatedUsageTime);
    }
}
