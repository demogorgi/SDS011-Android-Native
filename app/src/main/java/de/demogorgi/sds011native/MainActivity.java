package de.demogorgi.sds011native;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.demogorgi.sds011native.model.AppModel;
import de.demogorgi.sds011native.model.HistoryPoint;
import de.demogorgi.sds011native.model.MeasurementMode;
import de.demogorgi.sds011native.sds.Reading;
import de.demogorgi.sds011native.sds.Sds011Protocol;
import de.demogorgi.sds011native.sds.SensorConnection;
import de.demogorgi.sds011native.ui.DustChartView;

public final class MainActivity extends Activity implements SensorConnection.Listener {
    private static final int REQ_BLUETOOTH = 1001;
    private static final int REQ_LOCATION = 1002;
    private static final long GPS_MAX_AGE_MS = 15000L;
    private static final long SENSOR_SILENCE_TIMEOUT_MS = 15000L;
    private static final int BG = Color.rgb(244, 244, 242);
    private static final int SURFACE = Color.WHITE;
    private static final int TEXT = Color.rgb(20, 20, 18);
    private static final int MUTED = Color.rgb(102, 102, 96);
    private static final int BORDER = Color.rgb(218, 218, 212);
    private static final int PRIMARY = Color.rgb(23, 108, 128);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AppModel model = new AppModel();
    private final MeasurementRecorder recorder = new MeasurementRecorder();
    private final ExecutorService uploadExecutor = Executors.newSingleThreadExecutor();
    private final ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private final ArrayList<String> deviceLabels = new ArrayList<>();

    private SharedPreferences preferences;
    private BluetoothAdapter bluetoothAdapter;
    private SensorConnection connection;
    private ArrayAdapter<String> deviceAdapter;
    private Spinner deviceSpinner;
    private TextView connectionText;
    private TextView pm25Text;
    private TextView pm10Text;
    private TextView ageText;
    private TextView measurementText;
    private TextView pointsText;
    private TextView intervalText;
    private TextView recordingText;
    private TextView gpsText;
    private TextView uploadText;
    private EditText sensorIdInput;
    private DustChartView chartView;
    private Button connectButton;
    private Button disconnectButton;
    private Button meanButton;
    private Button rawButton;
    private Button startButton;
    private Button stopButton;

    private MeasurementMode pendingMode = MeasurementMode.LOCAL;
    private boolean rawChart;
    private long chartRangeSeconds;
    private boolean powerSaveActive;
    private boolean sensorSleeping;
    private long firstFrameAt;
    private long cycleStartedAt;
    private long nextWakeAt;
    private boolean recordingActive;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            drivePowerSave();
            updateUi();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getPreferences(MODE_PRIVATE);
        model.setIntervalSeconds(preferredInterval());
        pendingMode = preferredMode();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        connection = new SensorConnection(this);
        connection.setSilenceTimeoutMillis(SENSOR_SILENCE_TIMEOUT_MS);
        setContentView(buildContent());
        ensureBluetoothPermission();
        updateUi();
        handler.post(tick);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        if (connection != null) {
            stopMeasurement();
            connection.write(Sds011Protocol.buildSleepCommand(false));
            connection.write(Sds011Protocol.buildWorkPeriodCommand(0));
            connection.disconnect();
        }
        uploadExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BLUETOOTH) {
            loadDevices();
        } else if (requestCode == REQ_LOCATION) {
            if (hasLocationPermission()) {
                Toast.makeText(this, "Standortzugriff erteilt. Messfahrt erneut starten.",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Ohne Standortberechtigung keine GPS-Spur.",
                        Toast.LENGTH_LONG).show();
            }
            updateGpsUi();
        }
    }

    @Override
    public void onConnected() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                model.setConnected(true);
                setConnectionStatus("verbunden");
                applySensorInterval();
                updateUi();
            }
        });
    }

    @Override
    public void onReading(final Reading reading) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                handleReading(reading);
            }
        });
    }

    @Override
    public void onDisconnected(final String message) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                writePoint(model.finishCurrentMeanWindow());
                model.setConnected(false);
                resetPowerSave();
                connection.setExpectedSilenceMillis(SENSOR_SILENCE_TIMEOUT_MS);
                setConnectionStatus(message == null ? "getrennt" : message);
                updateUi();
            }
        });
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("SDS011 Native", 28, Typeface.BOLD, TEXT);
        root.addView(title);
        TextView subtitle = text("Feinstaubmessung direkt als Android-App", 14, Typeface.NORMAL, MUTED);
        root.addView(subtitle);

        root.addView(sensorCard());
        root.addView(currentCard());
        root.addView(chartCard());
        root.addView(measureCard());
        return scroll;
    }

    private View sensorCard() {
        LinearLayout card = card();
        card.addView(cardTitle("Sensor"));

        deviceSpinner = new Spinner(this);
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceLabels);
        deviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(deviceAdapter);
        card.addView(deviceSpinner, matchWrap());

        LinearLayout row = row();
        Button reload = button("Neu laden");
        reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ensureBluetoothPermission();
            }
        });
        row.addView(reload, weightWrap());

        connectButton = button("Verbinden");
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectSelectedDevice();
            }
        });
        row.addView(connectButton, weightWrap());

        disconnectButton = button("Trennen");
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectSensor();
            }
        });
        row.addView(disconnectButton, weightWrap());
        card.addView(row);

        connectionText = text("getrennt", 14, Typeface.NORMAL, MUTED);
        card.addView(connectionText);
        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < devices.size()) {
                    preferences.edit().putString("bluetooth_device",
                            devices.get(position).getAddress()).apply();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        return card;
    }

    private View currentCard() {
        LinearLayout card = card();
        card.addView(cardTitle("Aktuell"));

        LinearLayout values = row();
        pm25Text = valueBlock(values, "PM2.5", "- ug/m3");
        pm10Text = valueBlock(values, "PM10", "- ug/m3");
        card.addView(values);

        ageText = text("Noch kein Messwert", 14, Typeface.NORMAL, MUTED);
        card.addView(ageText);
        return card;
    }

    private View chartCard() {
        LinearLayout card = card();
        card.addView(cardTitle("Verlauf"));

        LinearLayout series = row();
        meanButton = button("Mittelwerte");
        meanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rawChart = false;
                refreshChart();
                updateUi();
            }
        });
        series.addView(meanButton, weightWrap());
        rawButton = button("Rohwerte");
        rawButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rawChart = true;
                refreshChart();
                updateUi();
            }
        });
        series.addView(rawButton, weightWrap());
        card.addView(series);

        LinearLayout ranges = row();
        addRangeButton(ranges, "10 min", 600L);
        addRangeButton(ranges, "1 h", 3600L);
        addRangeButton(ranges, "6 h", 21600L);
        addRangeButton(ranges, "Alles", 0L);
        Button clear = button("Plot leeren");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                model.clearPlot();
                refreshChart();
                Toast.makeText(MainActivity.this, "Plot geleert.", Toast.LENGTH_SHORT).show();
            }
        });
        ranges.addView(clear, weightWrap());
        card.addView(ranges);

        chartView = new DustChartView(this);
        card.addView(chartView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));
        return card;
    }

    private View measureCard() {
        LinearLayout card = card();
        card.addView(cardTitle("Messung"));

        LinearLayout modes = row();
        addModeButton(modes, "Messfahrt", MeasurementMode.TRIP);
        addModeButton(modes, "Lokal", MeasurementMode.LOCAL);
        addModeButton(modes, "Stationaer", MeasurementMode.STATIONARY);
        card.addView(modes);

        LinearLayout intervals = row();
        for (int seconds : AppModel.NORMAL_INTERVALS) {
            addIntervalButton(intervals, seconds);
        }
        card.addView(intervals);
        intervalText = text("", 14, Typeface.NORMAL, MUTED);
        card.addView(intervalText);
        sensorIdInput = new EditText(this);
        sensorIdInput.setSingleLine(true);
        sensorIdInput.setText(preferences.getString("sensor_community_id", ""));
        sensorIdInput.setHint("sensor.community Sensor-ID (X-Sensor)");
        card.addView(sensorIdInput, matchWrap());
        uploadText = text("Stationärmodus sendet Werte öffentlich an sensor.community",
                13, Typeface.NORMAL, MUTED);
        card.addView(uploadText);

        LinearLayout actions = row();
        startButton = button("Start");
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startMeasurement();
            }
        });
        actions.addView(startButton, weightWrap());
        stopButton = button("Stopp");
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopMeasurement();
            }
        });
        actions.addView(stopButton, weightWrap());
        card.addView(actions);

        measurementText = text("inaktiv", 18, Typeface.BOLD, TEXT);
        pointsText = text("Punkte 0", 14, Typeface.NORMAL, MUTED);
        recordingText = text("Noch keine Messdatei", 12, Typeface.NORMAL, MUTED);
        gpsText = text("GPS nur bei Messfahrt", 14, Typeface.NORMAL, MUTED);
        card.addView(measurementText);
        card.addView(pointsText);
        card.addView(recordingText);
        card.addView(gpsText);
        return card;
    }

    private void addRangeButton(LinearLayout parent, String label, final long seconds) {
        Button button = button(label);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chartRangeSeconds = seconds;
                refreshChart();
            }
        });
        parent.addView(button, weightWrap());
    }

    private void addModeButton(LinearLayout parent, String label, final MeasurementMode mode) {
        Button button = button(label);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pendingMode = mode;
                preferences.edit().putString("measurement_mode", mode.name()).apply();
                updateUi();
            }
        });
        parent.addView(button, weightWrap());
    }

    private void addIntervalButton(LinearLayout parent, final int seconds) {
        Button button = button(intervalLabel(seconds));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (model.getMode() == MeasurementMode.STATIONARY
                        && seconds < AppModel.POWER_SAVE_MIN_INTERVAL) {
                    Toast.makeText(MainActivity.this,
                            "Stationaer nur 5 oder 15 min.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (model.getMode() != MeasurementMode.INACTIVE) {
                    writePoint(model.finishCurrentMeanWindow());
                }
                model.setIntervalSeconds(seconds);
                preferences.edit().putInt("interval_seconds", seconds).apply();
                applySensorInterval();
                updateUi();
            }
        });
        parent.addView(button, weightWrap());
    }

    private TextView valueBlock(LinearLayout parent, String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView top = text(label, 13, Typeface.BOLD, MUTED);
        TextView bottom = text(value, 28, Typeface.BOLD, TEXT);
        box.addView(top);
        box.addView(bottom);
        parent.addView(box, weightWrap());
        return bottom;
    }

    private void ensureBluetoothPermission() {
        if (!hasBluetoothPermission()) {
            if (Build.VERSION.SDK_INT >= 31) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BLUETOOTH);
            }
            return;
        }
        loadDevices();
    }

    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void loadDevices() {
        devices.clear();
        deviceLabels.clear();
        if (bluetoothAdapter == null) {
            deviceLabels.add("Bluetooth nicht verfuegbar");
        } else if (!hasBluetoothPermission()) {
            deviceLabels.add("Bluetooth-Berechtigung fehlt");
        } else {
            Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : bonded) {
                devices.add(device);
                String name = device.getName();
                deviceLabels.add((name == null || name.length() == 0 ? "Unbenannt" : name)
                        + "  " + device.getAddress());
            }
            if (devices.isEmpty()) {
                deviceLabels.add("Kein gekoppeltes Geraet");
            }
        }
        if (deviceAdapter != null) {
            deviceAdapter.notifyDataSetChanged();
            restoreSelectedDevice();
        }
        updateUi();
    }

    private void restoreSelectedDevice() {
        String preferredAddress = preferences.getString("bluetooth_device", null);
        if (preferredAddress == null || deviceSpinner == null) {
            return;
        }
        for (int i = 0; i < devices.size(); i++) {
            if (preferredAddress.equals(devices.get(i).getAddress())) {
                deviceSpinner.setSelection(i);
                return;
            }
        }
    }

    private void connectSelectedDevice() {
        if (!hasBluetoothPermission()) {
            ensureBluetoothPermission();
            return;
        }
        int pos = deviceSpinner == null ? -1 : deviceSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= devices.size()) {
            Toast.makeText(this, "Erst ein gekoppeltes SDS011-Geraet waehlen.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        preferences.edit().putString("bluetooth_device", devices.get(pos).getAddress()).apply();
        setConnectionStatus("verbinde ...");
        connection.connect(bluetoothAdapter, devices.get(pos));
        updateUi();
    }

    private void disconnectSensor() {
        stopMeasurement();
        if (connection != null) {
            connection.write(Sds011Protocol.buildSleepCommand(false));
            connection.write(Sds011Protocol.buildWorkPeriodCommand(0));
            connection.disconnect();
        }
        model.setConnected(false);
        resetPowerSave();
        setConnectionStatus("getrennt");
        updateUi();
    }

    private void startMeasurement() {
        if (pendingMode == MeasurementMode.STATIONARY) {
            new AlertDialog.Builder(this)
                    .setTitle("Öffentlicher Upload")
                    .setMessage("Jeder neue Messmittelwert wird an sensor.community gesendet. "
                            + "Nur am registrierten Sensorstandort starten.")
                    .setPositiveButton("Upload starten", (dialog, which) ->
                            startMeasurementConfirmed())
                    .setNegativeButton("Abbrechen", null)
                    .show();
            return;
        }
        startMeasurementConfirmed();
    }

    private void startMeasurementConfirmed() {
        if (model.getMode() != MeasurementMode.INACTIVE) {
            Toast.makeText(this, "Erst die laufende Messung beenden.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!model.isConnected()) {
            Toast.makeText(this, "Erst Sensor verbinden.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pendingMode == MeasurementMode.STATIONARY
                && model.getIntervalSeconds() < AppModel.POWER_SAVE_MIN_INTERVAL) {
            model.setIntervalSeconds(AppModel.POWER_SAVE_MIN_INTERVAL);
            preferences.edit().putInt("interval_seconds",
                    AppModel.POWER_SAVE_MIN_INTERVAL).apply();
        }
        String sensorId = sensorIdInput.getText().toString().trim();
        if (pendingMode == MeasurementMode.STATIONARY
                && !sensorId.matches("[A-Za-z0-9_-]{4,128}")) {
            Toast.makeText(this, "Für Uploads eine gültige sensor.community Sensor-ID eintragen.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        preferences.edit().putString("sensor_community_id", sensorId).apply();
        if (pendingMode == MeasurementMode.TRIP && !hasLocationPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        try {
            File file = recorder.start(this, pendingMode);
            recordingActive = true;
            recordingText.setText("Aufzeichnung: " + file.getAbsolutePath());
        } catch (IOException exc) {
            Toast.makeText(this, "Messdatei kann nicht angelegt werden: "
                    + exc.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        model.setMode(pendingMode);
        startMeasurementForegroundService(pendingMode == MeasurementMode.TRIP);
        applySensorInterval();
        updateUi();
    }

    private void handleReading(Reading reading) {
        long now = SystemClock.elapsedRealtime();
        if (powerSaveActive) {
            if (firstFrameAt == 0L) {
                firstFrameAt = now;
                cycleStartedAt = now;
            }
        }
        boolean usable = !powerSaveActive
                || (cycleStartedAt > 0L && now - cycleStartedAt >= AppModel.POWER_SAVE_SETTLE_MS);
        HistoryPoint point = model.addReading(reading.pm25, reading.pm10, usable);
        writePoint(point);
        if (point != null && model.getMode() == MeasurementMode.STATIONARY) {
            uploadPoint(point);
        }
        if (powerSaveActive && cycleStartedAt > 0L
                && now - cycleStartedAt >= AppModel.POWER_SAVE_SETTLE_MS
                + AppModel.POWER_SAVE_SAMPLE_WINDOW_MS) {
            sensorSleeping = true;
            nextWakeAt = cycleStartedAt + model.getIntervalSeconds() * 1000L;
            firstFrameAt = 0L;
            cycleStartedAt = 0L;
            connection.setExpectedSilenceMillis((model.getIntervalSeconds() + 30L) * 1000L);
            connection.write(Sds011Protocol.buildSleepCommand(true));
        }
        refreshChart();
        updateUi();
    }

    private void applySensorInterval() {
        if (!model.isConnected()) {
            resetPowerSave();
            return;
        }
        int interval = model.getIntervalSeconds();
        if (interval >= AppModel.POWER_SAVE_MIN_INTERVAL) {
            if (!powerSaveActive) {
                powerSaveActive = true;
                sensorSleeping = false;
                firstFrameAt = 0L;
                cycleStartedAt = 0L;
                nextWakeAt = 0L;
            }
            connection.write(Sds011Protocol.buildWorkPeriodCommand(0));
            connection.write(Sds011Protocol.buildSleepCommand(false));
            connection.setExpectedSilenceMillis(SENSOR_SILENCE_TIMEOUT_MS);
        } else {
            resetPowerSave();
            connection.setExpectedSilenceMillis(SENSOR_SILENCE_TIMEOUT_MS);
            connection.write(Sds011Protocol.buildSleepCommand(false));
            connection.write(Sds011Protocol.buildWorkPeriodCommand(0));
        }
    }

    private void drivePowerSave() {
        if (!powerSaveActive || !sensorSleeping || !model.isConnected()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (nextWakeAt > 0L && now >= nextWakeAt) {
            sensorSleeping = false;
            firstFrameAt = 0L;
            cycleStartedAt = 0L;
            nextWakeAt = 0L;
            connection.setExpectedSilenceMillis(SENSOR_SILENCE_TIMEOUT_MS);
            connection.write(Sds011Protocol.buildSleepCommand(false));
        }
    }

    private void resetPowerSave() {
        powerSaveActive = false;
        sensorSleeping = false;
        firstFrameAt = 0L;
        cycleStartedAt = 0L;
        nextWakeAt = 0L;
        if (connection != null) {
            connection.setExpectedSilenceMillis(SENSOR_SILENCE_TIMEOUT_MS);
        }
    }

    private void stopMeasurement() {
        if (model.getMode() != MeasurementMode.INACTIVE) {
            writePoint(model.finishCurrentMeanWindow());
            model.setMode(MeasurementMode.INACTIVE);
            resetPowerSave();
            if (model.isConnected()) {
                connection.write(Sds011Protocol.buildSleepCommand(false));
                connection.write(Sds011Protocol.buildWorkPeriodCommand(0));
            }
        }
        stopMeasurementForegroundService();
        try {
            recorder.stop();
        } catch (IOException exc) {
            Toast.makeText(this, "Messdatei konnte nicht abgeschlossen werden: "
                    + exc.getMessage(), Toast.LENGTH_LONG).show();
        }
        if (recordingActive) {
            File file = recorder.getFile();
            recordingText.setText(file == null ? "Aufzeichnung beendet"
                    : "Gespeichert: " + file.getAbsolutePath());
            recordingActive = false;
        }
        updateUi();
    }

    private void writePoint(HistoryPoint point) {
        if (point == null || !point.recorded) {
            return;
        }
        try {
            Location fix = freshLocation();
            boolean trip = model.getMode() == MeasurementMode.TRIP;
            recorder.write(point, trip && fix != null ? fix.getLatitude() : null,
                    trip && fix != null ? fix.getLongitude() : null);
        } catch (IOException exc) {
            try {
                recorder.stop();
            } catch (IOException ignored) {
                // Der Schreibfehler wird bereits angezeigt.
            }
            recordingActive = false;
            recordingText.setText("Aufzeichnungsfehler: " + exc.getMessage());
            Toast.makeText(this, "Messdatei kann nicht geschrieben werden.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void uploadPoint(final HistoryPoint point) {
        if (!point.recorded || uploadExecutor.isShutdown()) {
            return;
        }
        final String sensorId = preferences.getString("sensor_community_id", "");
        uploadText.setText("Upload läuft …");
        uploadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String result = SensorCommunityUploader.upload(sensorId, point);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (uploadText != null) {
                                uploadText.setText(result);
                            }
                        }
                    });
                } catch (final IOException exc) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (uploadText != null) {
                                uploadText.setText("Upload fehlgeschlagen: " + exc.getMessage());
                            }
                        }
                    });
                }
            }
        });
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startMeasurementForegroundService(boolean trackLocation) {
        if (trackLocation && !hasLocationPermission()) {
            return;
        }
        try {
            Intent intent = new Intent(this, MeasurementForegroundService.class);
            intent.putExtra(MeasurementForegroundService.EXTRA_TRACK_LOCATION, trackLocation);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (RuntimeException exc) {
            Toast.makeText(this, "GPS-Hintergrunddienst konnte nicht gestartet werden.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void stopMeasurementForegroundService() {
        stopService(new Intent(this, MeasurementForegroundService.class));
    }

    private Location freshLocation() {
        Location location = MeasurementForegroundService.getLatestLocation();
        if (location == null
                || SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos()
                > GPS_MAX_AGE_MS * 1000000L) {
            return null;
        }
        return location;
    }

    private void updateGpsUi() {
        if (gpsText == null) {
            return;
        }
        if (model.getMode() != MeasurementMode.TRIP) {
            gpsText.setText("GPS nur bei Messfahrt");
            return;
        }
        Location fix = freshLocation();
        gpsText.setText(fix == null ? "GPS sucht Signal · keine frische Position"
                : String.format(Locale.GERMANY, "GPS aktuell · %.6f, %.6f",
                fix.getLatitude(), fix.getLongitude()));
    }

    private int preferredInterval() {
        int preferred = preferences.getInt("interval_seconds", 5);
        for (int interval : AppModel.NORMAL_INTERVALS) {
            if (interval == preferred) {
                return preferred;
            }
        }
        return 5;
    }

    private MeasurementMode preferredMode() {
        String value = preferences.getString("measurement_mode", MeasurementMode.LOCAL.name());
        try {
            MeasurementMode mode = MeasurementMode.valueOf(value);
            return mode == MeasurementMode.INACTIVE ? MeasurementMode.LOCAL : mode;
        } catch (IllegalArgumentException exc) {
            return MeasurementMode.LOCAL;
        }
    }

    private void refreshChart() {
        if (chartView != null) {
            chartView.setData(model.history(rawChart, chartRangeSeconds), rawChart);
        }
    }

    private void updateUi() {
        long now = System.currentTimeMillis();
        boolean connected = model.isConnected();
        boolean connecting = connection != null && connection.isRunning();
        if (connectButton != null) {
            connectButton.setEnabled(!connected && !connecting);
        }
        if (disconnectButton != null) {
            disconnectButton.setEnabled(connected || connecting);
        }
        if (pm25Text != null) {
            pm25Text.setText(formatValue(model.getCurrentPm25()));
            pm10Text.setText(formatValue(model.getCurrentPm10()));
            long age = model.getCurrentAgeSeconds(now);
            ageText.setText(age < 0 ? "Noch kein Messwert"
                    : "Letzter Rohwert vor " + age + " s"
                    + (powerSaveActive ? (sensorSleeping ? " · Sensor schlaeft" : " · Sensor wach") : ""));
        }
        if (meanButton != null) {
            tint(meanButton, !rawChart);
            tint(rawButton, rawChart);
        }
        if (measurementText != null) {
            MeasurementMode mode = model.getMode();
            String status = mode == MeasurementMode.INACTIVE ? "inaktiv"
                    : mode.label + " laeuft";
            measurementText.setText(status);
            pointsText.setText("Punkte " + model.getSessionPoints()
                    + " · " + model.getSessionElapsedSeconds(now) + " s");
            intervalText.setText("Messintervall " + intervalLabel(model.getIntervalSeconds())
                    + (powerSaveActive ? " · 15 s Einschwingen, 30 s Mittelung" : ""));
            stopButton.setEnabled(mode != MeasurementMode.INACTIVE);
        }
        updateGpsUi();
        refreshChart();
    }

    private void setConnectionStatus(String text) {
        if (connectionText != null) {
            connectionText.setText(text);
        }
    }

    private static String formatValue(double value) {
        if (value <= 0.0) {
            return "- ug/m3";
        }
        return String.format(Locale.GERMANY, "%.1f ug/m3", value);
    }

    private static String intervalLabel(int seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            return (seconds / 60) + " min";
        }
        return seconds + " s";
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(SURFACE);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), BORDER);
        card.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(16);
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setPadding(0, dp(4), 0, dp(4));
        return row;
    }

    private TextView cardTitle(String value) {
        return text(value, 20, Typeface.BOLD, TEXT);
    }

    private TextView text(String value, int sp, int style, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setTextColor(color);
        view.setPadding(0, dp(2), 0, dp(2));
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setMinHeight(dp(44));
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        return params;
    }

    private void tint(Button button, boolean active) {
        button.setTextColor(active ? Color.WHITE : TEXT);
        if (Build.VERSION.SDK_INT >= 21) {
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    active ? PRIMARY : Color.rgb(235, 235, 231)));
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
