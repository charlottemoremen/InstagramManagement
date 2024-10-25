package com.example.usagemanagement;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

public class RedirectActivity extends AppCompatActivity {
    private static final String TAG = "UsageLog";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_redirect);
        Log.d(TAG, "redirect");
    }
}
