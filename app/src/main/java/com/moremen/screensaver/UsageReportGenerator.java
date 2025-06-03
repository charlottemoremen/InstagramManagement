package com.moremen.screensaver;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import com.itextpdf.text.Document;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UsageReportGenerator {
    private static final String TAG = "UsageReportGenerator";
    private static final String MY_APP = "com.moremen.screensaver";
    private static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("MM-dd-yy hh:mm:ss a", Locale.getDefault());
    private Context context;

    public UsageReportGenerator(Context context) {
        this.context = context;
    }

    public void generateUsageReport() {
        // date range: previous week (not including today)
        Calendar endCalendar = Calendar.getInstance();
        endCalendar.add(Calendar.DAY_OF_YEAR, -1); // shift to yesterday
        Date exactEnd = getEndOfDay((Calendar) endCalendar.clone());

        Calendar startCalendar = Calendar.getInstance();
        startCalendar.add(Calendar.DAY_OF_YEAR, -7);
        Date exactStart = getStartOfDay((Calendar) startCalendar.clone());

        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String participantID = prefs.getString("ParticipantID", "Unknown");

        //pull memory information
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(mi);

        //pull device info
        String deviceBrand = Build.BRAND;
        String deviceModel = Build.MODEL;
        String deviceMan = Build.MANUFACTURER;

        // 1) build intro text
        String introText = "——USAGE REPORT START——\n"
                + "Participant ID: " + participantID +"\n"
                + "today's date: " + formatDate(Calendar.getInstance().getTime()) + "\n"
                + "time zone on phone: " + Calendar.getInstance().getTimeZone().getID() + "\n"
                + "date span: " + formatDate(exactStart) + " to " + formatDate(exactEnd) + "\n\n"
                + "available memory: " + mi.availMem + "\n"
                + "memory threshold: " + mi.threshold + "\n"
                + "low memory? : " + mi.lowMemory + "\n\n"
                + "Brand: " + deviceBrand + "\n"
                + "Model: " + deviceModel + "\n"
                + "Manufacturer: " + deviceMan + "\n\n";

        // 2) gather usage data for the date span
        List<UsageStats> usageStatsList = queryUsageStats(exactStart, exactEnd);
        List<AppUsage> appUsages = calculateAppUsage(usageStatsList, exactStart, exactEnd);
        Collections.sort(appUsages, Comparator.comparingLong(AppUsage::getTotalUsage).reversed());

        // pick top 5
        List<AppUsage> top5Apps = appUsages.subList(0, Math.min(5, appUsages.size()));

        // 3) build daily usage table or fallback
        String[][] tableData;
        if (top5Apps.isEmpty()) {
            // fallback single-cell table so pdf won't be empty
            tableData = new String[][]{{"no usage data"}};
        } else {
            tableData = buildDailyTableData(top5Apps, exactStart, exactEnd);
        }

        // 4) build the kill event and raw data text
        StringBuilder rawBuilder = new StringBuilder();
        String appDeathLog = logAppDeathEvents(context, exactStart.getTime(), exactEnd.getTime());
        if (appDeathLog != null) {
            rawBuilder.append(appDeathLog);
        }
        rawBuilder.append("\n———RAW DATA———\n");
        appendRawData(rawBuilder, exactStart, exactEnd);
        rawBuilder.append("\n——USAGE REPORT END——\n");
        String rawText = rawBuilder.toString();

        // 5) produce pdf with intro text at top, the table next, then raw data
        saveReportAsPdf(introText, tableData, rawText);
    }

    private List<AppUsage> calculateAppUsage(List<UsageStats> usageStatsList, Date start, Date end) {
        long daysInRange = getDaysBetween(start, end);
        Map<String, Long> appUsageMap = new HashMap<>();

        for (UsageStats stats : usageStatsList) {
            long totalTime = stats.getTotalTimeInForeground();
            if (totalTime > 0) {
                appUsageMap.put(stats.getPackageName(), appUsageMap.getOrDefault(stats.getPackageName(), 0L) + totalTime);
            }
        }

        List<AppUsage> appUsages = new ArrayList<>();
        for (Map.Entry<String, Long> entry : appUsageMap.entrySet()) {
            appUsages.add(new AppUsage(entry.getKey(), entry.getValue(), daysInRange));
        }
        return appUsages;
    }

    private String[][] buildDailyTableData(List<AppUsage> topApps, Date start, Date end) {
        // (1) gather usage day by day
        Map<String, Map<String, Long>> dayAppUsage = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        cal.setTime(start);

        while (!cal.getTime().after(end)) {
            Calendar dayStartCal = (Calendar) cal.clone();
            Calendar dayEndCal = (Calendar) cal.clone();
            Date dayStart = getStartOfDay(dayStartCal);
            Date dayEnd = getEndOfDay(dayEndCal);
            String dayStr = formatDate(dayStart);

            // retrieve usage events for this day
            List<UsageEvent> events = queryUsageEvents(dayStart.getTime(), dayEnd.getTime());
            Map<String, Long> usageMap = new HashMap<>();
            Map<String, Long> resumedMap = new HashMap<>();

            for (UsageEvent e : events) {
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    resumedMap.put(e.packageName, e.timestamp);
                } else if (e.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                    Long startTs = resumedMap.remove(e.packageName);
                    if (startTs != null && isValidSession(startTs, e.timestamp)) {
                        long dur = e.timestamp - startTs;
                        usageMap.put(e.packageName, usageMap.getOrDefault(e.packageName, 0L) + dur);
                    }
                }
            }
            dayAppUsage.put(dayStr, usageMap);

            // next day
            cal.add(Calendar.DAY_OF_YEAR, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }

        // (2) create header row: "date" plus top app names
        int numCols = topApps.size() + 1;
        List<String[]> rows = new ArrayList<>();

        String[] headerRow = new String[numCols];
        headerRow[0] = "date";
        for (int i = 0; i < topApps.size(); i++) {
            headerRow[i + 1] = shortenPackageName(topApps.get(i).getPackageName());
        }
        rows.add(headerRow);

        // track totals
        Map<String, Long> grandTotals = new HashMap<>();

        // (3) fill day rows
        Calendar printCal = Calendar.getInstance();
        printCal.setTime(start);

        while (!printCal.getTime().after(end)) {
            String dayStr = formatDate(printCal.getTime());
            String[] row = new String[numCols];
            row[0] = dayStr;

            Map<String, Long> usageMap = dayAppUsage.get(dayStr);
            for (int i = 0; i < topApps.size(); i++) {
                AppUsage app = topApps.get(i);
                long ms = (usageMap != null) ? usageMap.getOrDefault(app.getPackageName(), 0L) : 0L;

                String fm = formatMinutesSeconds(ms);
                row[i + 1] = fm;

                grandTotals.put(app.getPackageName(), grandTotals.getOrDefault(app.getPackageName(), 0L) + ms);
            }
            rows.add(row);
            printCal.add(Calendar.DAY_OF_YEAR, 1);
        }

        // (4) add total row
        String[] totalRow = new String[numCols];
        totalRow[0] = "total";
        for (int i = 0; i < topApps.size(); i++) {
            long sum = grandTotals.getOrDefault(topApps.get(i).getPackageName(), 0L);
            totalRow[i + 1] = formatMinutesSeconds(sum);
        }
        rows.add(totalRow);

        // (5) add average row
        String[] avgRow = new String[numCols];
        avgRow[0] = "avg";
        long daysCount = getDaysBetween(start, end);
        for (int i = 0; i < topApps.size(); i++) {
            long sum = grandTotals.getOrDefault(topApps.get(i).getPackageName(), 0L);
            long avgMs = (daysCount == 0) ? 0 : sum / daysCount;
            avgRow[i + 1] = formatMinutesSeconds(avgMs);
        }
        rows.add(avgRow);

        // convert to a 2D array
        String[][] tableData = new String[rows.size()][];
        for (int r = 0; r < rows.size(); r++) {
            tableData[r] = rows.get(r);
        }
        return tableData;
    }

    // helper to strip extraneous info from package names
    private String shortenPackageName(String packageName) {
        String shortName = packageName;
        shortName = shortName.replace("com.", "");
        shortName = shortName.replace(".android", "");
        shortName = shortName.replace("moremen.", "");
        return shortName;
    }

    // helper to convert milliseconds to "Xm Ys"
    private String formatMinutesSeconds(long durationMs) {
        if (durationMs <= 0) {
            return "0m 0s";
        }
        long minutes = durationMs / 60000;
        long seconds = (durationMs % 60000) / 1000;
        return minutes + "m " + seconds + "s";
    }

    private void appendRawData(StringBuilder report, Date start, Date end) {
        Calendar currentDate = Calendar.getInstance();
        currentDate.setTime(start);

        while (!currentDate.getTime().after(end)) {
            // day header
            String dayStr = formatDate(currentDate.getTime());
            report.append(dayStr).append(":\n");

            long startOfDay = getStartOfDay(currentDate).getTime();
            long endOfDay = getEndOfDay(currentDate).getTime();

            // gather usage events for this day
            List<UsageEvent> usageEvents = queryUsageEvents(startOfDay, endOfDay);
            if (usageEvents.isEmpty()) {
                report.append("No phone usage\n\n");
                currentDate.add(Calendar.DAY_OF_YEAR, 1);
                continue;
            }

            // build a chronological list of (app, start, end, duration) by pairing ACTIVITY_RESUMED -> ACTIVITY_PAUSED
            // 1) sort by timestamp
            usageEvents.sort(Comparator.comparingLong(e -> e.timestamp));

            // 2) create sessions
            List<SessionEntry> sessions = new ArrayList<>();
            Map<String, Long> sessionStartMap = new HashMap<>();

            for (UsageEvent e : usageEvents) {
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    sessionStartMap.put(e.packageName, e.timestamp);
                } else if (e.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                    Long startTs = sessionStartMap.remove(e.packageName);
                    if (startTs != null && isValidSession(startTs, e.timestamp)) {
                        long dur = e.timestamp - startTs;

                        // enforce at least 1s if >0
                        if (dur > 0 && dur < 1000) {
                            dur = 1000;
                        }
                        sessions.add(new SessionEntry(shortenPackageName(e.packageName), startTs, e.timestamp, dur));
                    }
                }
            }

            if (sessions.isEmpty()) {
                // means we never got a valid resumed->paused pair
                report.append("No phone usage\n\n");
                currentDate.add(Calendar.DAY_OF_YEAR, 1);
                continue;
            }

            // sessions are already sorted by startTime, so print
            for (SessionEntry s : sessions) {
                report.append(s.appName).append(": ").append(formatTimeRange(s.startTime, s.endTime)).append(" (").append(formatDuration(s.durationMs)).append(")\n");
            }

            //print a 2-col table of total daily usage by app
            Map<String, Long> dailyUsageMap = new HashMap<>();
            for (SessionEntry s : sessions) {
                dailyUsageMap.put(s.appName, dailyUsageMap.getOrDefault(s.appName, 0L) + s.durationMs);
            }

            report.append("\n App\t\t total daily time \n");
            for (Map.Entry<String, Long> entry : dailyUsageMap.entrySet()) {
                String app = entry.getKey();
                long totalMs = entry.getValue();
                report.append(app).append(":\t\t").append(formatMinutesSeconds(totalMs)).append("\n");
            }
            report.append("\n");

            // move to next day
            currentDate.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    // helper method to query usage events
    private List<UsageEvent> queryUsageEvents(long start, long end) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            Log.w(TAG, "UsageStatsManager is null.");
            return new ArrayList<>();
        }

        UsageEvents events = usageStatsManager.queryEvents(start, end);
        List<UsageEvent> usageEvents = new ArrayList<>();

        if (events != null) {
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event != null && (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED || event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED)) {
                    usageEvents.add(new UsageEvent(event.getPackageName(), event.getTimeStamp(), event.getEventType()));
                }
            }
        }
        return usageEvents;
    }

    private List<UsageStats> queryUsageStats(Date start, Date end) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            Log.w(TAG, "UsageStatsManager is null.");
            return new ArrayList<>();
        }

        return usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start.getTime(), end.getTime());
    }

    private Date getStartOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private Date getEndOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTime();
    }

    private long getDaysBetween(Date start, Date end) {
        long diff = end.getTime() - start.getTime();
        return (diff / (1000 * 60 * 60 * 24)) + 1;
    }

    private String formatDate(Date date) {
        return new SimpleDateFormat("MM-dd-yy", Locale.getDefault()).format(date);
    }

    private void saveReportAsPdf(String introText, String[][] tableData, String rawText) {
        String fileName = new SimpleDateFormat("MM-dd_HH:mm", Locale.getDefault()).format(new Date()) + "_UsageReport.pdf";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf");
        values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri uri = context.getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);

        if (uri != null) {
            try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                Document document = new Document();
                PdfWriter.getInstance(document, os);
                document.open();

                // write intro text
                Font normalFont = FontFactory.getFont(FontFactory.COURIER, 10);
                document.add(new Paragraph(introText, normalFont));

                // write table in the middle
                if (tableData != null && tableData.length > 0) {
                    PdfPTable pdfTable = new PdfPTable(tableData[0].length);
                    pdfTable.setWidthPercentage(100f);

                    for (String[] row : tableData) {
                        for (String cellText : row) {
                            PdfPCell cell = new PdfPCell(new Paragraph(cellText, normalFont));
                            pdfTable.addCell(cell);
                        }
                    }
                    document.add(pdfTable);
                }
                document.add(new Paragraph(rawText, normalFont));

                document.close();
                Log.i(TAG, "pdf usage report saved to downloads: " + uri);
            } catch (Exception e) {
                Log.e(TAG, "error creating pdf", e);
            }
        } else {
            Log.e(TAG, "failed to create pdf file in downloads");
        }
    }

    private String formatDuration(long durationMillis) {
        if (durationMillis < 0 || durationMillis > 24 * 60 * 60 * 1000) {
            return "timekeeping anomaly"; // Prevent extreme durations
        }

        long minutes = durationMillis / 60000;
        long seconds = (durationMillis % 60000) / 1000;

        // Ensure at least 1 second is displayed for nonzero durations
        if (minutes == 0 && seconds == 0 && durationMillis > 0) {
            seconds = 1;  // Force 1 second minimum for small durations
        }

        return minutes + " min " + seconds + " sec";
    }

    private String formatTimeRange(long start, long end) {
        return dateTimeFormat.format(new Date(start)) + " - " + dateTimeFormat.format(new Date(end));
    }

    private boolean isValidSession(long start, long end) {
        return start > 0 && end > 0 && end > start && (end - start) < (24 * 60 * 60 * 1000); // ensure durations < 1 day
    }

    private String logAppDeathEvents(Context context, long startTime, long endTime) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return null;
        StringBuilder logBuilder = new StringBuilder("——— SCREENSAVER KILL & RESTART EVENTS ———\n");

        List<ApplicationExitInfo> exitInfoList = activityManager.getHistoricalProcessExitReasons(MY_APP, 0, Integer.MAX_VALUE);
        if (exitInfoList.isEmpty()) {
            logBuilder.append("No app death or restart events found.\n");
            return logBuilder.toString();
        }

        // collect only kills within the date span
        List<Long> killTimestamps = new ArrayList<>();
        Map<Long, String> killReasonMap = new HashMap<>();
        for (ApplicationExitInfo exitInfo : exitInfoList) {
            long ts = exitInfo.getTimestamp();
            if (ts >= startTime && ts <= endTime) {
                killTimestamps.add(ts);
                killReasonMap.put(ts, getExitReasonText(exitInfo.getReason()));
            }
        }

        if (killTimestamps.isEmpty()) {
            logBuilder.append("No kills found during date span.\n");
            return logBuilder.toString();
        } else {
            Collections.sort(killTimestamps);
        }

        // load all ACTIVITY_RESUMED events in the span
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        List<UsageEvent> resumedEvents = new ArrayList<>();
        if (usm != null) {
            UsageEvents usageEvents = usm.queryEvents(startTime, endTime);
            UsageEvents.Event event = new UsageEvents.Event();
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event);
                if (event != null && MY_APP.equals(event.getPackageName()) && event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    resumedEvents.add(new UsageEvent(event.getPackageName(), event.getTimeStamp(), event.getEventType()));
                }
            }
        }

        // match each kill to the next resumed event
        for (long killTs : killTimestamps) {
            String killTimeStr = formatTime(killTs);
            String reason = killReasonMap.getOrDefault(killTs, "Unknown");
            String restartStr = "";
            for (UsageEvent e : resumedEvents) {
                if (e.timestamp > killTs) {
                    restartStr = formatTime(e.timestamp);
                    break;
                }
            }
            if (!restartStr.isEmpty()) {
                logBuilder.append("ScreenSaver killed at ").append(killTimeStr).append(" (").append(reason).append("), restarted at ").append(restartStr).append("\n");
            } else {
                logBuilder.append("ScreenSaver killed at ").append(killTimeStr).append(" (").append(reason).append("), no restart detected in span\n");
            }
        }
        return logBuilder.toString();
    }

    // Convert reason codes to human-readable text
    private String getExitReasonText(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_ANR:
                return "ANR (App Not Responding)";
            case ApplicationExitInfo.REASON_CRASH:
                return "Crash";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
                return "Native Crash";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED:
                return "Dependency Process Died";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                return "Excessive Resource Usage";
            case ApplicationExitInfo.REASON_EXIT_SELF:
                return "Exited Normally";
            case ApplicationExitInfo.REASON_FREEZER:
                return "Background Freezing (Low Memory)";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                return "Initialization Failure";
            case ApplicationExitInfo.REASON_LOW_MEMORY:
                return "Low Memory Kill";
            case ApplicationExitInfo.REASON_OTHER:
                return "Other";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE:
                return "Permissions Changed";
            case ApplicationExitInfo.REASON_SIGNALED:
                return "Killed by Signal";
            case ApplicationExitInfo.REASON_UNKNOWN:
            default:
                return "Unknown";
        }
    }

    private String formatTime(long timestamp) {
        return new SimpleDateFormat("MM/dd/yy hh:mm a", Locale.getDefault()).format(new Date(timestamp));
    }

    private static class SessionEntry {
        String appName;
        long startTime;
        long endTime;
        long durationMs;

        SessionEntry(String appName, long startTime, long endTime, long durationMs) {
            this.appName = appName;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMs = durationMs;
        }
    }

    private static class UsageEvent {
        String packageName;
        long timestamp;
        int eventType;

        public UsageEvent(String packageName, long timestamp, int eventType) {
            this.packageName = packageName;
            this.timestamp = timestamp;
            this.eventType = eventType;
        }
    }

    private static class AppUsage {
        private final String packageName;
        private final long totalUsage;
        private final long daysInRange;

        public AppUsage(String packageName, long totalUsage, long daysInRange) {
            this.packageName = packageName;
            this.totalUsage = totalUsage;
            this.daysInRange = daysInRange;
        }

        public String getPackageName() {
            return packageName;
        }

        public long getTotalUsage() {
            return totalUsage;
        }
    }
}