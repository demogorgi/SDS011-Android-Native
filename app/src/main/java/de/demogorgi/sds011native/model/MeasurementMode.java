package de.demogorgi.sds011native.model;

import de.demogorgi.sds011native.R;

public enum MeasurementMode {
    INACTIVE("inaktiv", R.string.mode_observe_title),
    TRIP("Messfahrt", R.string.mode_trip_title),
    LOCAL("Lokale Messung", R.string.mode_local_title),
    STATIONARY("Stationaer", R.string.mode_stationary_title);

    public final String label;
    public final int labelRes;

    MeasurementMode(String label, int labelRes) {
        this.label = label;
        this.labelRes = labelRes;
    }
}
