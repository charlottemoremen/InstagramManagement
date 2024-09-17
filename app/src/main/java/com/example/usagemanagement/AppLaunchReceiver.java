package com.example.usagemanagement;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.util.Log;

import java.util.List;

public class AppLaunchReceiver extends BroadcastReceiver {

    private static final String TAG = "AppLaunchReceiver";
    private static final long MAX_USAGE_TIME = 60 * 60 * 1000; // 1 hour in milliseconds

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "BroadcastReceiver triggered");
        String action = intent.getAction();
        if (Intent.ACTION_MAIN.equals(action)) {
            Log.d(TAG, "Intent action is MAIN");

            if (intent.getComponent() != null) {
                String packageName = intent.getComponent().getPackageName();
                Log.d(TAG, "Package Name: " + packageName);

                if ("com.instagram.android".equals(packageName)) {
                    UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
                    if (usageStatsManager != null) {
                        long endTime = System.currentTimeMillis();
                        long startTime = endTime - 24 * 60 * 60 * 1000; // last 24 hours

                        List<UsageStats> stats = usageStatsManager.queryUsageStats(
                                UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

                        boolean shouldRedirect = false;
                        for (UsageStats stat : stats) {
                            if ("com.instagram.android".equals(stat.getPackageName())) {
                                long totalTimeForeground = stat.getTotalTimeInForeground();
                                Log.d(TAG, "Instagram usage time: " + totalTimeForeground);

                                if (totalTimeForeground >= MAX_USAGE_TIME) {
                                    shouldRedirect = true;
                                    break;
                                }
                            }
                        }

                        if (shouldRedirect) {
                            Intent redirectIntent = new Intent(context, MainActivity.class);
                            redirectIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(redirectIntent);
                            Log.i(TAG, "Redirecting to MainActivity due to excessive Instagram usage");
                        }
                    }
                }
            }
        }
    }
}
