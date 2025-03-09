package com.example.usagemanagement;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class InstagramUsageTracker {

    private static final String TAG = "InstagramUsageTracker";
    private static final String INSTAGRAM_PACKAGE_NAME = "com.instagram.android";
    private static final long CHECK_INTERVAL_MS = 1000; // check every second
    private static final long TIMEKEEPING_ERROR_THRESHOLD_MS = 5000; // 5 seconds

    private final Context context;
    private final Handler handler;

    private long IGSessionStartTime = 0;
    private long lastIGUsageStats = 0;
    private long sessionTime = 0;
    private long lastResetTime = 0;
    private long lastInactiveTime = 0;

    private boolean isInstagramActive = false;
    private InstagramUsageListener usageListener;

    private long sessionBaseUsage = 0;


    public InstagramUsageTracker(Context context) {
        this.context = context;
        this.handler = new Handler();
        resetDailyTracking();
    }

    public void setInstagramUsageListener(InstagramUsageListener listener) {
        this.usageListener = listener;
    }

    public void startTracking() {
        // initialize the last known usage stats value
        lastIGUsageStats = getInstagramUsageToday();
        handler.postDelayed(this::trackInstagramUsage, CHECK_INTERVAL_MS);
    }


    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder timeString = new StringBuilder();
        if (hours > 0) timeString.append(hours).append(" hr ");
        if (minutes > 0) timeString.append(minutes).append(" min ");
        if (secs > 0 || timeString.length() == 0) timeString.append(secs).append(" sec");

        return timeString.toString().trim();
    }

    private void trackInstagramUsage() {
        long currentUsageStatsTime = getInstagramUsageToday();
        long currentTime = System.currentTimeMillis();

        // reset stats at midnight
        if (hasDayChanged(currentTime)) {
            resetDailyTracking();
        }

        MyAccessibilityService service = MyAccessibilityService.getInstance();
        boolean isCurrentlyActive = (service != null && service.isAppCurrentlyActive(INSTAGRAM_PACKAGE_NAME));

        long computedUsageTime;
        if (isCurrentlyActive) {
            if (!isInstagramActive) {
                // session just started; record the base events usage
                if (lastInactiveTime > 0 && (currentTime - lastInactiveTime) < TIMEKEEPING_ERROR_THRESHOLD_MS) {
                    Log.d(TAG, "Instagram resumed quickly, continuing session.");
                } else {
                    IGSessionStartTime = currentTime;
                    sessionBaseUsage = currentUsageStatsTime;
                    sessionTime = 0;
                    Log.d(TAG, "Instagram detected as open at: " + IGSessionStartTime);
                }
                isInstagramActive = true;
            } else {
                sessionTime = currentTime - IGSessionStartTime;
            }
            computedUsageTime = sessionBaseUsage + sessionTime;
        } else {
            if (isInstagramActive) {
                // session ended; log duration and reset tracking
                isInstagramActive = false;
                logSessionDuration();
                lastInactiveTime = currentTime;
            }
            computedUsageTime = currentUsageStatsTime;
        }

        notifyInstagramUsage(computedUsageTime);
        lastIGUsageStats = currentUsageStatsTime;
        Log.d(TAG, "Estimated daily usage: " + formatTime(computedUsageTime));
        handler.postDelayed(this::trackInstagramUsage, CHECK_INTERVAL_MS);
    }


    private boolean hasDayChanged(long currentTime) {
        Calendar lastReset = Calendar.getInstance();
        lastReset.setTimeInMillis(lastResetTime);

        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(currentTime);

        boolean dayChanged = now.get(Calendar.DAY_OF_YEAR) != lastReset.get(Calendar.DAY_OF_YEAR);
        if (dayChanged) {
            resetDailyTracking();
        }
        return dayChanged;
    }

    private void resetDailyTracking() {
        lastResetTime = System.currentTimeMillis();
        lastIGUsageStats = 0; // force reset
        Log.d(TAG, "daily tracking reset. last ig usage stats cleared.");
    }

    private void logSessionDuration() {
        if (IGSessionStartTime > 0) {
            long sessionEndTime = System.currentTimeMillis();
            long sessionDuration = sessionEndTime - IGSessionStartTime;

            long updatedIGUsageStats = getInstagramUsageToday();
            long trackedDurationChange = updatedIGUsageStats - lastIGUsageStats;
            long discrepancy = Math.abs(trackedDurationChange - sessionDuration);

            Log.d(TAG, "instagram session ended. duration: " + sessionDuration + " ms (" +
                    (sessionDuration / 1000) + " seconds)");
            Log.d(TAG, "tracked usage time change: " + trackedDurationChange + " ms (" +
                    (trackedDurationChange / 1000) + " seconds)");
            Log.d(TAG, "discrepancy between manual and tracked usage: " + discrepancy + " ms (" +
                    (discrepancy / 1000) + " seconds)");

            lastIGUsageStats = updatedIGUsageStats;
        }
    }

    private void notifyInstagramUsage(long usageStatsTime) {
        if (usageListener != null) {
            usageListener.onInstagramUsageUpdated(usageStatsTime);
        }
    }

    public long getInstagramUsageToday() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            Log.w(TAG, "usagestatsmanager is null.");
            return 0;
        }

        // set the time range for today
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
        if (usageEvents == null) {
            Log.w(TAG, "usagestatsmanager returned null for events.");
            return 0;
        }

        long totalForegroundTime = 0;
        long lastOpenedTime = -1;

        UsageEvents.Event event = new UsageEvents.Event();
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            if (INSTAGRAM_PACKAGE_NAME.equals(event.getPackageName())) {
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastOpenedTime = event.getTimeStamp();
                } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED && lastOpenedTime != -1) {
                    long sessionTime = event.getTimeStamp() - lastOpenedTime;
                    totalForegroundTime += sessionTime;
                    lastOpenedTime = -1;
                }
            }
        }

        Log.d(TAG, "total instagram usage for today (calculated via events): " + formatTime(totalForegroundTime));
        return totalForegroundTime;
    }

    public interface InstagramUsageListener {
        void onInstagramUsageUpdated(long estimatedUsageTime);
    }
}
