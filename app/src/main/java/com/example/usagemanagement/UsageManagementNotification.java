package com.example.usagemanagement;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

public class UsageManagementNotification {

    public static void showAppClosedNotification(final Activity activity) {
        if (!activity.isFinishing()) {  // Check if activity is still valid
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    new AlertDialog.Builder(activity)
                            .setTitle("UsageManagement Stopped")
                            .setMessage("You closed UsageManagement and have turned off its functionalities. Did you mean to do that?")
                            .setPositiveButton("Reopen App", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // Reopen the app
                                    reopenApp(activity);
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            });
        }
    }

    // Reopen the app from the alert dialog
    private static void reopenApp(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        }
    }
}
