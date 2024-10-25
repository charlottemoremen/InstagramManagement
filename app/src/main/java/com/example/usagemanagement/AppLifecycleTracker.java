package com.example.usagemanagement;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

public class AppLifecycleTracker implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "AppLifecycleTracker";
    private int activityReferences = 0;
    private boolean isActivityChangingConfigurations = false;

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        Log.d(TAG, activity.getLocalClassName() + " created.");
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (++activityReferences == 1 && !isActivityChangingConfigurations) {
            // App enters foreground
            Log.d(TAG, "App entered foreground.");
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        Log.d(TAG, activity.getLocalClassName() + " resumed.");
    }

    @Override
    public void onActivityPaused(Activity activity) {
        Log.d(TAG, activity.getLocalClassName() + " paused.");
    }

    @Override
    public void onActivityStopped(Activity activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations();
        if (--activityReferences == 0 && !isActivityChangingConfigurations) {
            // App enters background, do not notify user here
            Log.d(TAG, "App entered background.");
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        Log.d(TAG, activity.getLocalClassName() + " destroyed.");
        // Check if the entire app is closing, not just one activity
        if (activityReferences == 0 && !isActivityChangingConfigurations) {
            Log.d(TAG, "App is closing, showing 'Did you mean to exit?' dialog.");
            // Show dialog only if the entire app is being closed
            UsageManagementNotification.showAppClosedNotification(activity);
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

}
