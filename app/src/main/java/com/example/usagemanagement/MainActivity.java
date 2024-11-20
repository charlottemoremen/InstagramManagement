package com.example.usagemanagement;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity
        implements MyAccessibilityService.AccessibilityServiceConnectionListener {

    private static final String TAG = "MainActivity";
    private TextView statusTextView;
    private Handler handler;
    private InstagramUsageTracker tracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        if (statusTextView == null) {
            Log.e("MainActivity", "statusTextView is null. Check your layout XML.");
            return; // Prevent further execution if the TextView is not found
        }
        handler = new Handler();

        // Set the listener for AccessibilityService connection
        MyAccessibilityService.setAccessibilityServiceConnectionListener(this);

        // Check and handle accessibility on start
        checkAndHandleAccessibility();

        tracker = new InstagramUsageTracker(this);

        tracker.setInstagramUsageListener(estimatedTime -> runOnUiThread(() -> {
            TextView statusTextView = findViewById(R.id.statusTextView);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(estimatedTime);
            long seconds = TimeUnit.MILLISECONDS.toSeconds(estimatedTime) % 60;
            statusTextView.setText(String.format("Instagram usage: %d min %d sec", minutes, seconds));
        }));

        tracker.startTracking();
    }

    private void checkAndHandleAccessibility() {
        if (MyAccessibilityService.getInstance() != null) {
            Log.i(TAG, "Accessibility already enabled.");
            onAccessibilityServiceConnected();
        } else {
            Log.i(TAG, "Accessibility not enabled. Prompting user.");
            statusTextView.setText("Please enable accessibility access.");
            openAccessibilitySettings();
        }
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        // Periodically check if accessibility service is enabled
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (MyAccessibilityService.getInstance() != null) {
                    Log.i(TAG, "Accessibility enabled.");
                    onAccessibilityServiceConnected();
                } else {
                    Log.i(TAG, "Waiting for accessibility to be enabled...");
                    handler.postDelayed(this, 1000); // Check every second
                }
            }
        }, 1000);
    }

    @Override
    public void onAccessibilityServiceConnected() {
        Log.i(TAG, "Accessibility service connection detected in MainActivity.");
        statusTextView.setText("Tracking Instagram usage...");
        startTrackingInstagramUsage();
    }

    private void startTrackingInstagramUsage() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateInstagramUsage(); // Call method with no parameters
                handler.postDelayed(this, 5000); // Update every 5 seconds
            }
        }, 0);
    }

    private void updateInstagramUsage() {
        if (statusTextView == null) {
            Log.e("MainActivity", "Cannot update Instagram usage. statusTextView is null.");
            return;
        }

        long usageTime = tracker.getInstagramUsageToday();

        long minutes = TimeUnit.MILLISECONDS.toMinutes(usageTime);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(usageTime) % 60;
        statusTextView.setText(String.format("Instagram usage: %d min %d sec", minutes, seconds));
    }
}
