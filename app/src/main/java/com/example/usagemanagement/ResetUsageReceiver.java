package com.example.usagemanagement;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ResetUsageReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        MainActivity mainActivity = new MainActivity();
        //mainActivity.resetInstagramUsageTime(); // Reset usage time
        Log.d("UsageTracker", "Instagram usage time has been reset to 0 at midnight.");
    }
}
