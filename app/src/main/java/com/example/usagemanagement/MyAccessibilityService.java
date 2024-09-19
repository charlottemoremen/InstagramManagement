package com.example.usagemanagement;

import android.accessibilityservice.AccessibilityService;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.Calendar;
import java.util.List;

public class MyAccessibilityService extends AccessibilityService {

    private static final String TAG = "MyAccessibilityService";
    private static final String INSTAGRAM_PACKAGE_NAME = "com.instagram.android";
    private static final long ONE_MINUTE_IN_MILLIS = 60000; //

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() != null) {
            String packageName = event.getPackageName().toString();
            Log.d(TAG, packageName + " opened");

            if (INSTAGRAM_PACKAGE_NAME.equals(packageName)) {
                Log.d(TAG, "Instagram opened");

                // Check if total Instagram usage since local midnight exceeds 1 minute
                if (getInstagramUsageSinceMidnight() > ONE_MINUTE_IN_MILLIS) {
                    Log.d(TAG, "Instagram usage exceeds 1 minute, redirecting to UsageManagement app");

                    //redirect
                    Intent intent = new Intent(MyAccessibilityService.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Required when launching an activity from a service
                    startActivity(intent);
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
        // Handle interruptions if needed
    }

    /**
     * Get the total Instagram usage time since local midnight.
     */
    private long getInstagramUsageSinceMidnight() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);

        if (usageStatsManager != null) {
            // Get current time
            long endTime = System.currentTimeMillis();

            // Get local midnight time
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startTime = calendar.getTimeInMillis();

            // Query the usage stats for Instagram since midnight
            List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

            if (stats != null) {
                for (UsageStats stat : stats) {
                    if (INSTAGRAM_PACKAGE_NAME.equals(stat.getPackageName())) {
                        // Return total Instagram usage time in milliseconds
                        return stat.getTotalTimeInForeground();
                    }
                }
            }
        }
        return 0;
    }
}
