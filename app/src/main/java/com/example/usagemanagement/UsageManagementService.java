package com.example.usagemanagement;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class UsageManagementService extends Service {

    public static final String CHANNEL_ID = "UsageManagementServiceChannel";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Create a notification channel (for Android O and above)
        createNotificationChannel();

        // Create a persistent notification
        Intent notificationIntent = new Intent(this, MainActivity.class);

        // Add FLAG_IMMUTABLE to PendingIntent for Android 12+
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        // Build the notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Usage Management Running")
                .setContentText("Tracking Instagram usage in the background.")
                .setSmallIcon(R.drawable.ic_notification)  // Ensure this icon exists
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Optional, ensure proper notification behavior
                .build();

        // Start the service in the foreground with the notification
        startForeground(1, notification);

        return START_STICKY; // Ensures the service is restarted if killed by the system
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Create notification channel for Android O and above
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Usage Management Service",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
