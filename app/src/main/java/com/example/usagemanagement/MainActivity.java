package com.example.usagemanagement;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity
        implements MyAccessibilityService.AccessibilityServiceConnectionListener {

    private static final String TAG = "MainActivity";
    private TextView statusTextView;
    private TextView usageTitleText;
    private Handler handler;
    private Button grayscaleButton;
    private Button puzzleButton;
    private boolean isGrayscaleEnabled = false;
    private InstagramUsageTracker tracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Correctly initialize UI elements
        usageTitleText = findViewById(R.id.usageTitleText);
        statusTextView = findViewById(R.id.IGTrackingText);
        grayscaleButton = findViewById(R.id.grayscaleButton);
        puzzleButton = findViewById(R.id.puzzleButton);

        if (statusTextView == null || grayscaleButton == null || puzzleButton == null) {
            Log.e(TAG, "UI elements are null. Check your layout XML.");
            return;
        }

        // Set button background colors
        grayscaleButton.setBackgroundColor(Color.parseColor("#FFD8E4"));
        puzzleButton.setBackgroundColor(Color.parseColor("#FFD8E4"));

        handler = new Handler();

        // Greyscale button functionality
        grayscaleButton.setOnClickListener(v -> {
            isGrayscaleEnabled = !isGrayscaleEnabled;
            grayscaleButton.setBackgroundColor(isGrayscaleEnabled ? Color.parseColor("#90EE90") : Color.parseColor("#FFD8E4"));
            MyAccessibilityService service = MyAccessibilityService.getInstance();
            if (service != null) {
                service.setGrayscaleEnabled(isGrayscaleEnabled);
            }
        });

        // Connect the Puzzle button to PuzzleActivity
        puzzleButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PuzzleActivity.class);
            startActivity(intent);
        });

        // Accessibility connection
        MyAccessibilityService.setAccessibilityServiceConnectionListener(this);
        checkAndHandleAccessibility();

        tracker = new InstagramUsageTracker(this);

        // Update Instagram usage time
        tracker.setInstagramUsageListener(estimatedTime -> runOnUiThread(() -> {
            if (statusTextView != null) {
                long minutes = TimeUnit.MILLISECONDS.toMinutes(estimatedTime);
                long seconds = TimeUnit.MILLISECONDS.toSeconds(estimatedTime) % 60;
                statusTextView.setText(String.format("%d min %d sec", minutes, seconds));
            }
        }));

        tracker.startTracking();
    }

    private void checkAndHandleAccessibility() {
        if (MyAccessibilityService.getInstance() != null) {
            Log.i(TAG, "Accessibility already enabled.");
            statusTextView.setText("Tracking Instagram usage...");
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
                    handler.postDelayed(this, 500);
                }
            }
        }, 500);
    }

    @Override
    public void onAccessibilityServiceConnected() {
        Log.i(TAG, "Accessibility service connection detected in MainActivity.");
        statusTextView.setText("Tracking Instagram usage...");
    }
}
