package com.example.usagemanagement;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import android.app.AppOpsManager;
import android.view.WindowManager;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity
        implements MyAccessibilityService.AccessibilityServiceConnectionListener {

    private static final String TAG = "MainActivity";
    private TextView statusTextView;
    private TextView usageTitleText;
    private TextView IDText;
    private Handler handler;
    private Button reportButton;
    private boolean isGrayscaleEnabled = false;
    private InstagramUsageTracker tracker;
    private static final long THRESHOLD = 60;
    private static final long PUZZLE_PROMPT_INTERVAL = 15;

    private int lastSolvedPuzzleInterval = 0;
    private int pendingPuzzleInterval = 0;
    private boolean solvedPuzzle = true;
    private boolean puzzleLaunched = false;
    private static final int PUZZLE_REQUEST_CODE = 1001;
    private ActivityResultLauncher<Intent> puzzleActivityLauncher;
    private static final int POST_NOTIFICATIONS_REQUEST_CODE = 1010;

    private void assignCondition() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String participantID = prefs.getString("ParticipantID", null);

        // A: control
        // B: grayscale
        // C: puzzle
        // D: grayscale AND puzzle

        if (participantID == null) {
            // REAL condition assignment
//            char[] conditions = {'A', 'B', 'C', 'D'};
//            char assignedCondition = conditions[new Random().nextInt(conditions.length)];

            //FOR BETA TESTING PURPOSES ONLY
            char[] conditions = {'B', 'C', 'D'};
            char assignedCondition = conditions[new Random().nextInt(conditions.length)];

            // Generate a random 4-digit number
            int randomID = 1000 + new Random().nextInt(9000);
            participantID = assignedCondition + String.valueOf(randomID);

            // Save to SharedPreferences
            prefs.edit().putString("ParticipantID", participantID).apply();
        }
        // Display the assigned participant ID
        IDText.setText("Your participant number is " + participantID);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        handler = new Handler();

        // Correctly initialize UI elements
        usageTitleText = findViewById(R.id.usageTitleText);
        statusTextView = findViewById(R.id.IGTrackingText);
        IDText = findViewById(R.id.IDText);
        reportButton = findViewById(R.id.reportButton);

        if (statusTextView == null) {
            Log.e(TAG, "UI elements are null. Check your layout XML.");
            return;
        }

        checkPermissionsInOrder();

        puzzleActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null &&
                            result.getData().getBooleanExtra("puzzleSolved", false)) {

                        lastSolvedPuzzleInterval = pendingPuzzleInterval;
                        solvedPuzzle = true;
                        savePuzzleState(true);
                        setPuzzleSessionActive(false);
                        getSharedPreferences("PuzzlePrefs", MODE_PRIVATE)
                                .edit()
                                .remove("puzzleInProgressInterval")
                                .apply();
                        puzzleLaunched = false;
                        Log.d(TAG, "Puzzle solved, allowing Instagram access.");

                    } else {
                        // puzzle was NOT solved
                        solvedPuzzle = false;
                        savePuzzleState(false);
                        Log.d(TAG, "Puzzle not solved; keeping redirection active.");
                    }
                }
        );

        reportButton.setOnClickListener(v -> {
            UsageReportGenerator reportGenerator = new UsageReportGenerator(MainActivity.this);
            reportGenerator.generateUsageReport();
            Toast.makeText(MainActivity.this, "Usage report generated and saved!", Toast.LENGTH_SHORT).show();
        });

        // Accessibility connection
        MyAccessibilityService.setAccessibilityServiceConnectionListener(this);
        checkAndHandleAccessibility();

        tracker = new InstagramUsageTracker(this);
        assignCondition();

        // Update Instagram usage time
        tracker.setInstagramUsageListener(estimatedTime -> runOnUiThread(() -> {
            long totalUsageMinutes = TimeUnit.MILLISECONDS.toMinutes(estimatedTime);
            int currentPuzzleInterval = (int) (totalUsageMinutes / PUZZLE_PROMPT_INTERVAL);
            pendingPuzzleInterval = currentPuzzleInterval;

            long secondsUsed = TimeUnit.MILLISECONDS.toSeconds(estimatedTime) % 60;
            long minutesUsed = TimeUnit.MILLISECONDS.toMinutes(estimatedTime) % 60;
            long hoursUsed = TimeUnit.MILLISECONDS.toHours(estimatedTime);

            StringBuilder timeString = new StringBuilder();

            if (hoursUsed > 0) {
                timeString.append(hoursUsed).append(" hr ");
            }
            if (minutesUsed > 0 || hoursUsed > 0) { // Show minutes if there are hours, or if minutes exist
                timeString.append(minutesUsed).append(" min ");
            }
            if (secondsUsed > 0 || timeString.length() == 0) { // Show seconds if there are no minutes or hours
                timeString.append(secondsUsed).append(" sec");
            }

            boolean puzzleAbandoned = getSharedPreferences("PuzzlePrefs", MODE_PRIVATE)
                    .getBoolean("puzzleAbandoned", false);

            if (puzzleAbandoned) {
                puzzleLaunched = false;
                // reset the flag for future checks
                getSharedPreferences("PuzzlePrefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("puzzleAbandoned", false)
                        .apply();
            }

            String participantID = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                    .getString("ParticipantID", "");
            char condition = participantID.charAt(0);

            MyAccessibilityService service = MyAccessibilityService.getInstance();
            boolean isInstagramActive = service != null && service.isAppCurrentlyActive("com.instagram.android");

            if (statusTextView != null) {
                if (estimatedTime > 0) {
                    statusTextView.setText(timeString.toString().trim());
                } else {
                    statusTextView.setText("No Instagram usage detected today");
                }
            }
            if (minutesUsed >= THRESHOLD && isInstagramActive) {
                if (condition == 'B' || condition == 'D') {
                    service.setGrayscaleEnabled(true);
                    Log.d(TAG, "GRAYSCALE APPLIED");
                }
                if (condition == 'C' || condition == 'D') {
                    handlePuzzleLogic(currentPuzzleInterval);
                }
            } else {
                if (service != null) {
                    service.setGrayscaleEnabled(false);
                    Log.d(TAG, "GRAYSCALE DISABLED");
                }
            }
        }));
        tracker.startTracking();
    }

    private void handlePuzzleLogic(int currentPuzzleInterval) {
        solvedPuzzle = getPuzzleState();
        SharedPreferences prefs = getSharedPreferences("PuzzlePrefs", MODE_PRIVATE);
        int puzzleInProgressInterval = prefs.getInt("puzzleInProgressInterval", -1);

        // if user advanced to new puzzle interval and hasn't launched puzzle yet
        if ((currentPuzzleInterval > lastSolvedPuzzleInterval) && !puzzleLaunched) {
            solvedPuzzle = false;
            savePuzzleState(false);

            // clear old puzzle pattern so puzzleActivity starts fresh
            prefs.edit()
                    .remove("currentPuzzlePattern")
                    .putInt("puzzleInProgressInterval", currentPuzzleInterval)
                    .apply();

            puzzleLaunched = true;
            setPuzzleSessionActive(true);

            Intent intent = new Intent(MainActivity.this, PuzzleActivity.class);
            puzzleActivityLauncher.launch(intent);

        } else if (!solvedPuzzle && !puzzleLaunched) {
            if (puzzleInProgressInterval == currentPuzzleInterval) {
                Log.d(TAG, "Puzzle for this interval is not solved yet, forcing puzzle again.");
                forcePuzzleCompletion(); // but this won't generate a brand-new puzzle pattern
            } else {
                // fallback in case puzzleInProgressInterval got cleared incorrectly
                forcePuzzleCompletion();
            }
        }
    }

    private void forcePuzzleCompletion() {
        solvedPuzzle = getPuzzleState();
        if (solvedPuzzle) {
            Log.d(TAG, "Puzzle already solved, skipping redirection.");
            return;
        }
        if (puzzleLaunched) {
            // puzzle is already launching or open
            Log.d(TAG, "Puzzle already launched, skipping duplicate.");
            return;
        }

        setPuzzleSessionActive(true);
        puzzleLaunched = true;

        Intent intent = new Intent(MainActivity.this, PuzzleActivity.class);
        puzzleActivityLauncher.launch(intent);
    }

    private void checkPermissionsInOrder() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent batteryIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            if (batteryIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(batteryIntent);
            } else {
                Log.w(TAG, "No system activity to handle REQUEST_IGNORE_BATTERY_OPTIMIZATIONS intent.");
            }
        }

        // 1) usage stats
        if (!isUsageStatsPermissionGranted()) {
            notifyAndRedirect("You must grant package usage stats permission!",
                    Settings.ACTION_USAGE_ACCESS_SETTINGS);
        }
        // 2) overlay
        else if (!isSystemAlertWindowPermissionGranted()) {
            notifyAndRedirect("You must grant system alert window permission!",
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        }
        // 3) accessibility
        else if (!isAccessibilityPermissionGranted()) {
            notifyAndRedirect("You must grant accessibility permission!",
                    Settings.ACTION_ACCESSIBILITY_SETTINGS);
        }
        else {
            Log.i(TAG, "All permissions are granted.");
        }
    }

    private boolean isUsageStatsPermissionGranted() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean isSystemAlertWindowPermissionGranted() {
        return Settings.canDrawOverlays(this);
    }

    private boolean isAccessibilityPermissionGranted() {
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        return service != null;
    }

    private void notifyAndRedirect(String message, String settingsAction) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        handler.postDelayed(() -> {
            Intent intent = new Intent(settingsAction);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }, 2000); // delay to show message
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PUZZLE_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null && data.getBooleanExtra("puzzleSolved", false)) {
                solvedPuzzle = true;
                Log.d(TAG, "******************puzzle solved successfully******************");
            } else {
                solvedPuzzle = false;
                Log.d(TAG, "**************puzzle not solved, access remains blocked*************");
            }
        }
    }

    private void savePuzzleState(boolean solved) {
        SharedPreferences prefs = getSharedPreferences("PuzzlePrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("solvedPuzzle", solved).apply();
    }

    private boolean getPuzzleState() {
        SharedPreferences prefs = getSharedPreferences("PuzzlePrefs", MODE_PRIVATE);
        return prefs.getBoolean("solvedPuzzle", false);
    }

    private void setPuzzleSessionActive(boolean isActive) {
        SharedPreferences prefs = getSharedPreferences("PuzzlePrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("puzzleActive", isActive).apply();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == POST_NOTIFICATIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Post Notifications permission granted!");
                statusTextView.setText("All permissions are granted. Enjoy the app!");
            } else {
                Log.w(TAG, "Post Notifications permission denied.");
                // optionally inform user or proceed with partial functionality
            }
        }
    }

    
    @Override
    public void onAccessibilityServiceConnected() {
        Log.i(TAG, "Accessibility service connection detected in MainActivity.");
        statusTextView.setText("Tracking Instagram usage...");
    }

}