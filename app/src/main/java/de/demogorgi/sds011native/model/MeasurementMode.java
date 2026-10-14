package de.demogorgi.sds011native.model;

public enum MeasurementMode {
    INACTIVE("inaktiv"),
    TRIP("Messfahrt"),
    LOCAL("Lokale Messung"),
    STATIONARY("Stationaer");

    public final String label;

    MeasurementMode(String label) {
        this.label = label;
    }
}
