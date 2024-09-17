package com.example.usagemanagement;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

public class AppUsageMonitorService extends Service {
    private static final String TAG = "AppUsageMonitorService";
    private static final int UPDATE_INTERVAL = 300000; // 5 minutes

    private Handler handler;
    private Runnable runnable;
    private Map<String, Long> appUsageMap = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        runnable = new Runnable() {
            @Override
            public void run() {
                updateAppUsage();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        handler.post(runnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            // Handle intent if needed
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(runnable);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateAppUsage() {
        if (hasUsageStatsPermission()) {
            // Fetch app usage data and update appUsageMap
            // Use your existing logic here
        } else {
            Toast.makeText(this, "Usage stats permission is required", Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasUsageStatsPermission() {
        return Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).contains(getPackageName());
    }
}
