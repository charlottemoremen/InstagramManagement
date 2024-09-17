package com.example.usagemanagement;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.accessibilityservice.AccessibilityService;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String INSTAGRAM_PACKAGE_NAME = "com.instagram.android";
    private static final String TAG = "MainActivity";
    private TextView usageTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usageTextView = findViewById(R.id.usageTextView);

        // Check if accessibility service is enabled
        if (!isAccessibilityServiceEnabled(this, MyAccessibilityService.class)) {
            // Open accessibility settings to enable the service
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }

        // Check if usage stats permission is granted
        if (!hasUsageStatsPermission()) {
            // Open usage access settings to request permission
            requestUsageStatsPermission();
        } else {
            // Display Instagram usage time
            displayInstagramUsageTime();
        }
    }

    private boolean isAccessibilityServiceEnabled(Context context, Class<? extends AccessibilityService> service) {
        // Get the list of enabled accessibility services
        String enabledServices = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        if (enabledServices == null) {
            return false; // No services are enabled
        }

        String colonSplitter = ":";
        // Split the enabled services into an array
        String[] colonSplit = TextUtils.split(enabledServices, colonSplitter);

        // Check if the service is in the enabled services list
        for (String componentName : colonSplit) {
            if (componentName.contains(service.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUsageStatsPermission() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return false;
        }
        try {
            // Check if the permission is granted
            List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, 0, System.currentTimeMillis());
            return stats != null && !stats.isEmpty();
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: UsageStats permission not granted.");
            return false;
        }
    }

    private void requestUsageStatsPermission() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivity(intent);
    }

    private void displayInstagramUsageTime() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);

        // Get current time
        long endTime = System.currentTimeMillis();

        // Calculate local midnight
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long startTime = calendar.getTimeInMillis(); // Local midnight

        Log.i(TAG, "Start Time (Local Midnight): " + startTime);
        Log.i(TAG, "End Time (Current Time): " + endTime);

        if (usageStatsManager != null) {
            List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

            if (stats != null) {
                long instagramUsageTime = 0;
                for (UsageStats stat : stats) {
                    if (INSTAGRAM_PACKAGE_NAME.equals(stat.getPackageName())) {
                        instagramUsageTime += stat.getTotalTimeInForeground();
                    }
                }

                // Convert usage time to hours and minutes
                long hours = instagramUsageTime / (1000 * 60 * 60);
                long minutes = (instagramUsageTime % (1000 * 60 * 60)) / (1000 * 60);

                String usageText = "Instagram usage time: " + hours + " hours " + minutes + " minutes";
                usageTextView.setText(usageText);
            } else {
                usageTextView.setText("No usage data available.");
            }
        } else {
            usageTextView.setText("UsageStatsManager not available.");
        }
    }
}
