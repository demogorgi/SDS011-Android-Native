package de.demogorgi.sds011native.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class AppModel {
    public static final int[] NORMAL_INTERVALS = {1, 5, 10, 30, 60, 300, 900};
    public static final int[] STATIONARY_INTERVALS = {300, 900};
    public static final int POWER_SAVE_MIN_INTERVAL = 300;
    public static final long POWER_SAVE_SETTLE_MS = 15000L;
    public static final long POWER_SAVE_SAMPLE_WINDOW_MS = 30000L;
    public static final int HISTORY_LIMIT = 4320;
    public static final int RAW_HISTORY_LIMIT = 7200;

    private final ArrayDeque<HistoryPoint> meanHistory = new ArrayDeque<>();
    private final ArrayDeque<HistoryPoint> rawHistory = new ArrayDeque<>();

    private MeasurementMode mode = MeasurementMode.INACTIVE;
    private int intervalSeconds = 5;
    private boolean connected;
    private double currentPm25;
    private double currentPm10;
    private long currentAtMillis;

    private long meanWindowStartMillis;
    private int meanSamples;
    private double meanSum25;
    private double meanSum10;
    private long sessionStartMillis;
    private int sessionPoints;

    public synchronized void setConnected(boolean connected) {
        this.connected = connected;
        if (!connected) {
            resetMeanWindow();
        }
    }

    public synchronized boolean isConnected() {
        return connected;
    }

    public synchronized void setIntervalSeconds(int seconds) {
        intervalSeconds = seconds;
        resetMeanWindow();
    }

    public synchronized int getIntervalSeconds() {
        return intervalSeconds;
    }

    public synchronized void setMode(MeasurementMode nextMode) {
        mode = nextMode == null ? MeasurementMode.INACTIVE : nextMode;
        if (mode == MeasurementMode.STATIONARY && intervalSeconds < POWER_SAVE_MIN_INTERVAL) {
            intervalSeconds = POWER_SAVE_MIN_INTERVAL;
        }
        sessionStartMillis = mode == MeasurementMode.INACTIVE ? 0L : System.currentTimeMillis();
        sessionPoints = 0;
        resetMeanWindow();
    }

    public synchronized MeasurementMode getMode() {
        return mode;
    }

    public synchronized int getSessionPoints() {
        return sessionPoints;
    }

    public synchronized long getSessionElapsedSeconds(long nowMillis) {
        if (sessionStartMillis == 0L) {
            return 0L;
        }
        return Math.max(0L, (nowMillis - sessionStartMillis) / 1000L);
    }

    public synchronized HistoryPoint addReading(double pm25, double pm10, boolean usable) {
        long now = System.currentTimeMillis();
        currentPm25 = pm25;
        currentPm10 = pm10;
        currentAtMillis = now;
        addLimited(rawHistory, new HistoryPoint(now, pm25, pm10, 1.0, false, usable),
                RAW_HISTORY_LIMIT);
        if (usable) {
            return addToMeanWindow(now, pm25, pm10);
        }
        return null;
    }

    public synchronized double getCurrentPm25() {
        return currentPm25;
    }

    public synchronized double getCurrentPm10() {
        return currentPm10;
    }

    public synchronized long getCurrentAgeSeconds(long nowMillis) {
        if (currentAtMillis == 0L) {
            return -1L;
        }
        return Math.max(0L, (nowMillis - currentAtMillis) / 1000L);
    }

    public synchronized List<HistoryPoint> history(boolean raw, long rangeSeconds) {
        ArrayDeque<HistoryPoint> source = raw ? rawHistory : meanHistory;
        long since = rangeSeconds > 0 ? System.currentTimeMillis() - rangeSeconds * 1000L : Long.MIN_VALUE;
        ArrayList<HistoryPoint> copy = new ArrayList<>();
        for (HistoryPoint point : source) {
            if (point.epochMillis >= since) {
                copy.add(point);
            }
        }
        return copy;
    }

    public synchronized void clearPlot() {
        meanHistory.clear();
        rawHistory.clear();
    }

    public synchronized HistoryPoint finishCurrentMeanWindow() {
        if (meanSamples <= 0) {
            return null;
        }
        return finishMeanWindow(System.currentTimeMillis(),
                Math.max(1.0, (System.currentTimeMillis() - meanWindowStartMillis) / 1000.0));
    }

    private HistoryPoint addToMeanWindow(long now, double pm25, double pm10) {
        if (meanWindowStartMillis == 0L) {
            meanWindowStartMillis = now;
        }
        meanSamples += 1;
        meanSum25 += pm25;
        meanSum10 += pm10;

        long intervalMillis = intervalSeconds * 1000L;
        boolean powerSaveWindowComplete =
                intervalSeconds >= POWER_SAVE_MIN_INTERVAL
                        && now - meanWindowStartMillis >= POWER_SAVE_SAMPLE_WINDOW_MS;
        boolean normalWindowComplete =
                intervalSeconds < POWER_SAVE_MIN_INTERVAL
                        && now - meanWindowStartMillis >= intervalMillis;
        if (powerSaveWindowComplete || normalWindowComplete) {
            return finishMeanWindow(now, powerSaveWindowComplete
                    ? POWER_SAVE_SAMPLE_WINDOW_MS / 1000.0
                    : Math.max(1.0, (now - meanWindowStartMillis) / 1000.0));
        }
        return null;
    }

    private HistoryPoint finishMeanWindow(long now, double interval) {
        if (meanSamples <= 0) {
            resetMeanWindow();
            return null;
        }
        boolean recorded = mode != MeasurementMode.INACTIVE;
        HistoryPoint point = new HistoryPoint(now, meanSum25 / meanSamples,
                meanSum10 / meanSamples, interval, recorded, true);
        addLimited(meanHistory, point, HISTORY_LIMIT);
        if (recorded) {
            sessionPoints += 1;
        }
        resetMeanWindow();
        return point;
    }

    private void resetMeanWindow() {
        meanWindowStartMillis = 0L;
        meanSamples = 0;
        meanSum25 = 0.0;
        meanSum10 = 0.0;
    }

    private static void addLimited(ArrayDeque<HistoryPoint> deque, HistoryPoint point, int limit) {
        deque.addLast(point);
        while (deque.size() > limit) {
            deque.removeFirst();
        }
    }
}
