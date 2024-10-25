package com.example.usagemanagement;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "UsageLog";
    private TextView usageTextView;
    private TextView instructionTextView;
    private TextView limitTextView;
    private Handler handler = new Handler();
    private Runnable usageUpdaterRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usageTextView = findViewById(R.id.usageTextView);
        instructionTextView = findViewById(R.id.instructionTextView);
        limitTextView = findViewById(R.id.limitTextView);

        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
        } else {
            initializeTracking();
        }
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        startActivity(intent);
        Log.i(TAG, "Requesting SYSTEM_ALERT_WINDOW permission.");
    }

    private void initializeTracking() {
        if (!isAccessibilityServiceEnabled(this, MyAccessibilityService.class)) {
            promptUserToEnableAccessibilityService();
            instructionTextView.setText("Please enable accessibility access to track Instagram usage.");
        } else {
            startUsageUpdater();
            displayInstagramUsageTime(MyAccessibilityService.getRealInstagramUsageTime(this));
        }
    }

    private boolean isAccessibilityServiceEnabled(Context context, Class<? extends android.accessibilityservice.AccessibilityService> service) {
        String serviceId = getPackageName() + "/" + service.getName();
        String enabledServices = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.contains(serviceId);
    }

    private void promptUserToEnableAccessibilityService() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Log.w(TAG, "Please enable the accessibility service for full functionality.");
    }

    private void startUsageUpdater() {
        fetchAndDisplayInstagramUsageTime();

        usageUpdaterRunnable = new Runnable() {
            @Override
            public void run() {
                fetchAndDisplayInstagramUsageTime();
                handler.postDelayed(this, 30000);
            }
        };
        handler.post(usageUpdaterRunnable);
    }

    private void fetchAndDisplayInstagramUsageTime() {
        MyAccessibilityService service = MyAccessibilityService.getServiceInstance();
        if (service != null) {
            long realInstagramUsageTime = service.getRealInstagramUsageTime(this);

            runOnUiThread(() -> {
                if (realInstagramUsageTime > 0) {
                    displayInstagramUsageTime(realInstagramUsageTime);
                } else {
                    usageTextView.setText("No Instagram usage tracked yet.");
                }
            });
        } else {
            Log.w(TAG, "MyAccessibilityService instance is null.");
        }
    }

    private void displayInstagramUsageTime(long instagramUsageTime) {
        long hours = instagramUsageTime / (1000 * 60 * 60);
        long minutes = (instagramUsageTime % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (instagramUsageTime % (1000 * 60)) / 1000;

        String usageText = "Instagram usage today: " + hours + " hours " + minutes + " minutes " + seconds + " seconds";
        usageTextView.setText(usageText);

        if (instagramUsageTime >= MyAccessibilityService.REDIRECT_LIMIT) {
            limitTextView.setText("You've used all your Instagram time for today. See you tomorrow!");
        } else {
            limitTextView.setText("");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Settings.canDrawOverlays(this)) {
            initializeTracking();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(usageUpdaterRunnable);
    }
}
