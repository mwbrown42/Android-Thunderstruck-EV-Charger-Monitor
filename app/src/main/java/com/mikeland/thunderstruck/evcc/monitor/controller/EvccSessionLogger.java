package com.mikeland.thunderstruck.evcc.monitor.controller;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Persistent session logger for EVCC gateway and charging interactions.
 * Persists all events, connection transitions, transmissions, and telemetry
 * to local storage so logs can be pulled and analyzed offline after testing in the car.
 */
public class EvccSessionLogger {
    private static final String TAG = "EvccSessionLogger";
    private static EvccSessionLogger instance;

    private File logFile;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final long MAX_LOG_SIZE = 1024 * 1024; // 1 MB max before truncation

    public static synchronized EvccSessionLogger getInstance() {
        if (instance == null) {
            instance = new EvccSessionLogger();
        }
        return instance;
    }

    private EvccSessionLogger() {}

    public synchronized void init(Context context) {
        try {
            File baseDir = context.getExternalFilesDir(null);
            if (baseDir == null) {
                baseDir = context.getFilesDir();
            }
            File logsDir = new File(baseDir, "logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            logFile = new File(logsDir, "evcc_session.log");
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                logFile.delete();
            }
            log("INIT", "==================================================");
            log("INIT", "EVCC Tablet Session Logging Initialized.");
            log("INIT", "Log File Path: " + logFile.getAbsolutePath());
            log("INIT", "==================================================");
        } catch (Exception e) {
            Log.e(TAG, "Failed to init EvccSessionLogger", e);
        }
    }

    public synchronized void log(String category, String message) {
        String timestamp = dateFormat.format(new Date());
        String logEntry = String.format(Locale.US, "[%s] [%s] %s", timestamp, category, message);
        Log.d(TAG, logEntry);

        if (logFile != null) {
            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(logEntry);
            } catch (Exception e) {
                Log.e(TAG, "Error writing to log file", e);
            }
        }
    }

    public synchronized String getRecentLogs(int maxLines) {
        if (logFile == null || !logFile.exists()) {
            return "No log file found.";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            LinkedList<String> lines = new LinkedList<>();
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() > maxLines) {
                    lines.removeFirst();
                }
            }
            for (String l : lines) {
                sb.append(l).append("\n");
            }
        } catch (Exception e) {
            return "Error reading log: " + e.getMessage();
        }
        return sb.toString();
    }

    public synchronized void clearLog() {
        if (logFile != null && logFile.exists()) {
            logFile.delete();
        }
        log("INIT", "Log cleared by user request.");
    }

    public File getLogFile() {
        return logFile;
    }
}

