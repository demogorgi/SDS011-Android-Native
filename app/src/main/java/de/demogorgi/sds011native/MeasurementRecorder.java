package de.demogorgi.sds011native;

import android.content.Context;
import android.os.Environment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.demogorgi.sds011native.model.HistoryPoint;
import de.demogorgi.sds011native.model.MeasurementMode;

final class MeasurementRecorder {
    private final SimpleDateFormat timestampFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY);
    private BufferedWriter writer;
    private File file;
    private BufferedWriter pm25Kml;
    private BufferedWriter pm10Kml;
    private File pm25KmlFile;
    private File pm10KmlFile;
    private double previousLatitude;
    private double previousLongitude;
    private double previousPm25;
    private double previousPm10;
    private boolean hasPreviousPosition;
    private boolean trip;

    File start(Context context, MeasurementMode mode) throws IOException {
        File documents = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (documents == null) {
            documents = new File(context.getFilesDir(), Environment.DIRECTORY_DOCUMENTS);
        }
        return start(new File(documents, "Feinstaub"), mode);
    }

    File start(File directory, MeasurementMode mode) throws IOException {
        stop();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Ausgabeordner kann nicht angelegt werden.");
        }

        String modeName = mode == MeasurementMode.TRIP ? "messfahrt"
                : mode == MeasurementMode.STATIONARY ? "stationaer" : "lokal";
        String fileTimestamp = new SimpleDateFormat("yyyyMMdd_HH_mm_ss_SSS", Locale.GERMANY)
                .format(new Date());
        file = new File(directory, "feinstaub_" + modeName + "_" + fileTimestamp + ".csv");
        writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), "UTF-8"));
        writer.write("Zeit;PM2.5;PM10;Breite;Laenge");
        writer.newLine();
        writer.flush();
        trip = mode == MeasurementMode.TRIP;
        if (trip) {
            pm25KmlFile = new File(directory, "feinstaub_25_line_" + fileTimestamp + ".kml");
            pm10KmlFile = new File(directory, "feinstaub_10_line_" + fileTimestamp + ".kml");
        }
        return file;
    }

    void write(HistoryPoint point, Double latitude, Double longitude) throws IOException {
        if (writer == null || point == null || !point.recorded) {
            return;
        }
        writer.write(timestampFormat.format(new Date(point.epochMillis)));
        writer.write(';');
        writer.write(String.format(Locale.GERMANY, "%.1f", point.pm25));
        writer.write(';');
        writer.write(String.format(Locale.GERMANY, "%.1f", point.pm10));
        writer.write(';');
        writer.write(latitude == null ? "" : String.format(Locale.US, "%.6f", latitude));
        writer.write(';');
        writer.write(longitude == null ? "" : String.format(Locale.US, "%.6f", longitude));
        writer.newLine();
        writer.flush();
        if (!trip) {
            return;
        }
        if (latitude == null || longitude == null) {
            hasPreviousPosition = false;
            return;
        }
        ensureKmlWriters();
        writeKmlPoint(pm25Kml, "PM2.5", point.pm25, previousPm25,
                latitude, longitude, previousLatitude, previousLongitude);
        writeKmlPoint(pm10Kml, "PM10", point.pm10, previousPm10,
                latitude, longitude, previousLatitude, previousLongitude);
        previousLatitude = latitude;
        previousLongitude = longitude;
        previousPm25 = point.pm25;
        previousPm10 = point.pm10;
        hasPreviousPosition = true;
    }

    void stop() throws IOException {
        IOException failure = null;
        try {
            closeKml(pm25Kml);
        } catch (IOException exc) {
            failure = exc;
        }
        try {
            closeKml(pm10Kml);
        } catch (IOException exc) {
            if (failure == null) {
                failure = exc;
            }
        }
        if (writer != null) {
            try {
                writer.flush();
            } finally {
                try {
                    writer.close();
                } finally {
                    writer = null;
                }
            }
        }
        pm25Kml = null;
        pm10Kml = null;
        hasPreviousPosition = false;
        trip = false;
        if (failure != null) {
            throw failure;
        }
    }

    File getFile() {
        return file;
    }

    private void ensureKmlWriters() throws IOException {
        if (pm25Kml == null) {
            pm25Kml = openKml(pm25KmlFile, "PM2.5");
        }
        if (pm10Kml == null) {
            pm10Kml = openKml(pm10KmlFile, "PM10");
        }
    }

    private BufferedWriter openKml(File kmlFile, String name) throws IOException {
        BufferedWriter result = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(kmlFile), "UTF-8"));
        result.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        result.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>\n");
        result.write("<name>" + name + " Feinstaubspur</name>\n");
        return result;
    }

    private void writeKmlPoint(BufferedWriter output, String name, double value,
                               double oldValue, double latitude, double longitude,
                               double oldLatitude, double oldLongitude) throws IOException {
        output.write("<Placemark><name>");
        output.write(name + " " + String.format(Locale.US, "%.1f", value));
        output.write("</name><Point><altitudeMode>relativeToGround</altitudeMode>");
        output.write("<coordinates>");
        output.write(String.format(Locale.US, "%.6f,%.6f,%.1f",
                longitude, latitude, value));
        output.write("</coordinates></Point>");
        if (hasPreviousPosition) {
            output.write("<LineString><altitudeMode>relativeToGround</altitudeMode>");
            output.write("<coordinates>");
            output.write(String.format(Locale.US, "%.6f,%.6f,%.1f %.6f,%.6f,%.1f",
                    oldLongitude, oldLatitude, oldValue, longitude, latitude, value));
            output.write("</coordinates></LineString><Style><LineStyle><color>");
            output.write(kmlColor(value));
            output.write("</color><width>8</width></LineStyle></Style>");
        }
        output.write("</Placemark>\n");
        output.flush();
    }

    private static String kmlColor(double value) {
        double bounded = Math.max(0.0, Math.min(50.0, value));
        int red;
        int green;
        if (bounded < 25.0) {
            red = (int) Math.round(bounded * 255.0 / 25.0);
            green = 255;
        } else {
            red = 255;
            green = (int) Math.round((50.0 - bounded) * 255.0 / 25.0);
        }
        return String.format(Locale.US, "c800%02x%02x", green, red);
    }

    private static void closeKml(BufferedWriter output) throws IOException {
        if (output != null) {
            try {
                output.write("</Document></kml>\n");
                output.flush();
            } finally {
                output.close();
            }
        }
    }
}
