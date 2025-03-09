package com.example.usagemanagement;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

public class UsageReportGenerator {
    private static final String TAG = "UsageReportGenerator";
    private final Context context;

    public UsageReportGenerator(Context context) {
        this.context = context;
    }

    public void generateUsageReport() {
        // define date range for usage
        Calendar endCalendar = Calendar.getInstance();
        endCalendar.add(Calendar.DAY_OF_YEAR, -1); // shift to "yesterday"
        Date exactEnd = getEndOfDay((Calendar) endCalendar.clone());

        Calendar startCalendar = Calendar.getInstance();
        startCalendar.add(Calendar.DAY_OF_YEAR, -7);
        Date exactStart = getStartOfDay((Calendar) startCalendar.clone());

        // 1) build the intro text
        StringBuilder introBuilder = new StringBuilder();
        introBuilder.append("——USAGE REPORT START——\n")
                .append("today's date: ").append(formatDate(Calendar.getInstance().getTime())).append("\n")
                .append("time zone on phone: ").append(Calendar.getInstance().getTimeZone().getID()).append("\n")
                .append("date span: ").append(formatDate(exactStart)).append(" to ")
                .append(formatDate(exactEnd)).append("\n\n");
        // store final intro text
        String introText = introBuilder.toString();

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
            tableData = new String[][] { { "no usage data" } };
        } else {
            tableData = buildDailyTableData(top5Apps, exactStart, exactEnd);
        }

        // 4) build the raw data text
        StringBuilder rawBuilder = new StringBuilder();
        rawBuilder.append("\n———RAW DATA———\n");
        appendRawData(rawBuilder, exactStart, exactEnd);
        rawBuilder.append("\n——USAGE REPORT END——\n");
        // store final raw text
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

    // builds a day-by-day usage table with lines, stripping "com."
    private String[][] buildDailyTableData(List<AppUsage> topApps, Date start, Date end) {
        // (1) gather usage day by day
        Map<String, Map<String, Long>> dayAppUsage = new HashMap<>();

        Calendar cal = Calendar.getInstance();
        cal.setTime(start);

        while (!cal.getTime().after(end)) {
            Calendar dayStartCal = (Calendar) cal.clone();
            Calendar dayEndCal   = (Calendar) cal.clone();
            Date dayStart = getStartOfDay(dayStartCal);
            Date dayEnd   = getEndOfDay(dayEndCal);

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
                long ms = (usageMap != null)
                        ? usageMap.getOrDefault(app.getPackageName(), 0L)
                        : 0L;

                String fm = formatMinutesSeconds(ms);
                row[i + 1] = fm;

                grandTotals.put(app.getPackageName(),
                        grandTotals.getOrDefault(app.getPackageName(), 0L) + ms);
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


    // helper to strip "com." from the front
    private String shortenPackageName(String packageName) {
        String shortName = packageName;

        // remove all occurrences of "android"
        shortName = shortName.replace("com.", "");
        shortName = shortName.replace(".android", "");
        shortName = shortName.replace("example.", "");

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

            // build a chronological list of (app, start, end, duration)
            // by pairing ACTIVITY_RESUMED -> ACTIVITY_PAUSED
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
                        sessions.add(new SessionEntry(
                                shortenPackageName(e.packageName),
                                startTs,
                                e.timestamp,
                                dur));
                    }
                }
            }

            if (sessions.isEmpty()) {
                // means we never got a valid resumed->paused pair
                report.append("No phone usage\n\n");
                currentDate.add(Calendar.DAY_OF_YEAR, 1);
                continue;
            }

            // sessions are already sorted by startTime, so just print them
            for (SessionEntry s : sessions) {
                report.append(s.appName).append(": ")
                        .append(formatTimeRange(s.startTime, s.endTime))
                        .append(" (")
                        .append(formatDuration(s.durationMs))
                        .append(")\n");
            }

            // now print a 2-col table of total daily usage by app
            // accumulate usage in a map
            Map<String, Long> dailyUsageMap = new HashMap<>();
            for (SessionEntry s : sessions) {
                dailyUsageMap.put(
                        s.appName,
                        dailyUsageMap.getOrDefault(s.appName, 0L) + s.durationMs
                );
            }

            report.append("\n App\t\t total daily time \n");
            for (Map.Entry<String, Long> entry : dailyUsageMap.entrySet()) {
                String app = entry.getKey();
                long totalMs = entry.getValue();
                report.append(app).append(":\t\t")
                        .append(formatMinutesSeconds(totalMs))
                        .append("\n");
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
                if (event != null && (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED ||
                        event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED)) {
                    usageEvents.add(new UsageEvent(event.getPackageName(), event.getTimeStamp(), event.getEventType()));
                }
            }
        }

        return usageEvents;
    }

    private static final SimpleDateFormat dateTimeFormat =
            new SimpleDateFormat("MM-dd-yy hh:mm:ss a", Locale.getDefault());


    private List<UsageStats> queryUsageStats(Date start, Date end) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            Log.w(TAG, "UsageStatsManager is null.");
            return new ArrayList<>();
        }

        return usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, start.getTime(), end.getTime());
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
        String fileName = new SimpleDateFormat("MM-dd_HH:mm", Locale.getDefault())
                .format(new Date()) + "_UsageReport.pdf";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf");
        values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri uri = context.getContentResolver().insert(
                MediaStore.Files.getContentUri("external"), values);

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

                // write raw data text last
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


    // optionally define a simple method to remove "com." and "android." across the entire content
    private String stripPrefixes(String text) {
        // naive replace all occurrences of "com." or "android."
        return text.replace("com.", "")
                .replace("android.", "");
    }

    private String formatDuration(long durationMillis) {
        if (durationMillis < 0 || durationMillis > 24 * 60 * 60 * 1000) {
            return "0 min 0 sec"; // Prevent extreme durations
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
        return dateTimeFormat.format(new Date(start)) + " - "
                + dateTimeFormat.format(new Date(end));
    }

    private boolean isValidSession(long start, long end) {
        return start > 0 && end > 0 && end > start && (end - start) < (24 * 60 * 60 * 1000); // ensure durations < 1 day
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

        public long getDailyAverage() {
            return totalUsage / daysInRange;
        }
    }
}