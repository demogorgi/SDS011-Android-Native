package de.demogorgi.sds011native.model;

public final class SensorSiteCoordinates {
    private SensorSiteCoordinates() {
    }

    public static Double parseLatitude(String value) {
        return parse(value, -90.0, 90.0);
    }

    public static Double parseLongitude(String value) {
        return parse(value, -180.0, 180.0);
    }

    private static Double parse(String value, double minimum, double maximum) {
        if (value == null) {
            return null;
        }
        try {
            double coordinate = Double.parseDouble(value.trim().replace(',', '.'));
            if (Double.isNaN(coordinate) || Double.isInfinite(coordinate)
                    || coordinate < minimum || coordinate > maximum) {
                return null;
            }
            return coordinate;
        } catch (NumberFormatException exc) {
            return null;
        }
    }
}
