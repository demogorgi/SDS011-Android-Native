package de.demogorgi.sds011native.model;

public final class HistoryPoint {
    public final long epochMillis;
    public final double pm25;
    public final double pm10;
    public final double intervalSeconds;
    public final boolean recorded;
    public final boolean usable;

    HistoryPoint(long epochMillis, double pm25, double pm10,
                 double intervalSeconds, boolean recorded, boolean usable) {
        this.epochMillis = epochMillis;
        this.pm25 = pm25;
        this.pm10 = pm10;
        this.intervalSeconds = intervalSeconds;
        this.recorded = recorded;
        this.usable = usable;
    }
}
