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

    File start(Context context, MeasurementMode mode) throws IOException {
        stop();
        File documents = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (documents == null) {
            documents = new File(context.getFilesDir(), Environment.DIRECTORY_DOCUMENTS);
        }
        File directory = new File(documents, "Feinstaub");
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
        return file;
    }

    void write(HistoryPoint point) throws IOException {
        if (writer == null || point == null || !point.recorded) {
            return;
        }
        writer.write(timestampFormat.format(new Date(point.epochMillis)));
        writer.write(';');
        writer.write(String.format(Locale.GERMANY, "%.1f", point.pm25));
        writer.write(';');
        writer.write(String.format(Locale.GERMANY, "%.1f", point.pm10));
        writer.write(";;");
        writer.newLine();
        writer.flush();
    }

    void stop() throws IOException {
        if (writer != null) {
            try {
                writer.flush();
            } finally {
                writer.close();
                writer = null;
            }
        }
    }

    File getFile() {
        return file;
    }
}
