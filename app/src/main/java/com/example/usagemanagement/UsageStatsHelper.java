package com.example.usagemanagement;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class UsageStatsHelper {

    // Method to retrieve Instagram usage time
    public long getInstagramUsageTime(Context context) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return 0;
        }

        // Get current time and the start of the day (midnight)
        long currentTime = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();

        // Query usage stats for today
        List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, currentTime);
        long instagramUsageTime = 0;

        // Loop through usage stats and find Instagram usage
        for (UsageStats usageStats : usageStatsList) {
            if (usageStats.getPackageName().equals("com.instagram.android")) {
                instagramUsageTime = usageStats.getTotalTimeInForeground();
                break;
            }
        }

        return instagramUsageTime;
    }

    // Helper method to convert milliseconds to a readable time format (minutes)
    public String formatUsageTime(long milliseconds) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) - TimeUnit.MINUTES.toSeconds(minutes);
        return minutes + " min " + seconds + " sec";
    }
}
