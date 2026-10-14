package de.demogorgi.sds011native.sds;

public final class Reading {
    public final double pm25;
    public final double pm10;
    public final int deviceId;

    Reading(double pm25, double pm10, int deviceId) {
        this.pm25 = pm25;
        this.pm10 = pm10;
        this.deviceId = deviceId;
    }
}
