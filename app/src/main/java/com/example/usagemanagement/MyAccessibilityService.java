package com.example.usagemanagement;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.content.Intent;
import android.util.Log;

public class MyAccessibilityService extends AccessibilityService {

    private static final String TAG = "MyAccessibilityService";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() != null) {
            String packageName = event.getPackageName().toString();
            Log.d(TAG, packageName + " opened");

            if (packageName.equals("com.instagram.android")) {
                Log.d(TAG, "Instagram opened");

                // Notify your app or update UI
                Intent intent = new Intent("com.example.usagemanagement.UPDATE_UI");
                intent.putExtra("package_name", packageName);
                sendBroadcast(intent);
            }
        }
    }

    @Override
    public void onInterrupt() {
        // Handle interruptions if needed
    }
}
