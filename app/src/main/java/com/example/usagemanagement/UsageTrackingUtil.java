package com.example.usagemanagement;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import java.util.Calendar;

public class UsageTrackingUtil {

    private static final String INSTAGRAM_PACKAGE_NAME = "com.instagram.android";

    // Check if overlay permission is granted
    public static boolean checkOverlayPermission(Context context) {
        return Settings.canDrawOverlays(context);
    }

    // Redirect user to manage overlay permission
    public static void requestOverlayPermission(Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    // Calculate Instagram usage time since midnight
    public static long getInstagramUsageTimeSinceMidnight(Context context) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager != null) {
            long endTime = System.currentTimeMillis();
            long startTime = getMidnightTimeMillis();

            UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
            UsageEvents.Event currentEvent = new UsageEvents.Event();

            long instagramUsageTimeToday = 0;
            long lastEventTimestamp = 0;

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(currentEvent);

                if (INSTAGRAM_PACKAGE_NAME.equals(currentEvent.getPackageName())) {
                    if (currentEvent.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        lastEventTimestamp = currentEvent.getTimeStamp();
                    } else if (currentEvent.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND && lastEventTimestamp != 0) {
                        instagramUsageTimeToday += (currentEvent.getTimeStamp() - lastEventTimestamp);
                        lastEventTimestamp = 0;
                    }
                }
            }
            return instagramUsageTimeToday;
        }
        return 0;
    }

    // Get midnight time in milliseconds
    public static long getMidnightTimeMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
