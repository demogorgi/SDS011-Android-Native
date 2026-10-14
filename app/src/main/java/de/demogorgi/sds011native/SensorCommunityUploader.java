package de.demogorgi.sds011native;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import de.demogorgi.sds011native.model.HistoryPoint;

final class SensorCommunityUploader {
    private static final String ENDPOINT =
            "https://api.luftdaten.info/v1/push-sensor-data/";

    static String upload(String sensorId, HistoryPoint point) throws IOException {
        if (sensorId == null || !sensorId.matches("[A-Za-z0-9_-]{4,128}")) {
            throw new UploadException("Ungültige sensor.community Sensor-ID.", false);
        }
        String body = buildPayload(point);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Pin", "1");
            connection.setRequestProperty("X-Sensor", sensorId);
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_CREATED) {
                String response = readError(connection.getErrorStream());
                throw new UploadException("HTTP " + status
                        + (response.length() == 0 ? "" : ": " + response),
                        status == 429 || status >= 500);
            }
            return "Upload erfolgreich · PM2.5 " + String.format(Locale.GERMANY, "%.1f",
                    point.pm25) + " · PM10 " + String.format(Locale.GERMANY, "%.1f", point.pm10);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static String buildPayload(HistoryPoint point) throws IOException {
        if (point == null || Double.isNaN(point.pm25) || Double.isInfinite(point.pm25)
                || Double.isNaN(point.pm10) || Double.isInfinite(point.pm10)
                || point.pm25 < 0.0 || point.pm10 < 0.0) {
            throw new UploadException("Ungültiger Messwert.", false);
        }
        return "{\"software_version\":\"SDS011-Android\","
                + "\"sensordatavalues\":["
                + "{\"value_type\":\"P1\",\"value\":\""
                + String.format(Locale.US, "%.1f", point.pm10) + "\"},"
                + "{\"value_type\":\"P2\",\"value\":\""
                + String.format(Locale.US, "%.1f", point.pm25) + "\"}]}";
    }

    private static String readError(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && response.length() < 300) {
                response.append(line);
            }
        }
        return response.toString();
    }

    static final class UploadException extends IOException {
        final boolean retryable;

        UploadException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }
    }

    private SensorCommunityUploader() {
    }
}
