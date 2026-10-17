package de.demogorgi.sds011native.sds;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

public final class SensorConnection {
    public interface Listener {
        void onConnected();
        void onReading(Reading reading);
        void onDisconnected(String message);
    }

    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Listener listener;
    private final FrameDecoder decoder = new FrameDecoder();

    private Thread worker;
    private BluetoothSocket socket;
    private OutputStream output;
    private volatile boolean running;
    private volatile long silenceTimeoutMillis = 15000L;
    private volatile long lastFrameAtMillis;
    private volatile boolean silenceTimedOut;
    // The ViewModel explicitly sleeps the SDS011 between power-save samples.
    // Silence is expected until it sends the matching wake command.
    private volatile boolean sensorSleeping;
    private long generation;

    public SensorConnection(Listener listener) {
        this.listener = listener;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public void setSilenceTimeoutMillis(long timeoutMillis) {
        silenceTimeoutMillis = Math.max(1000L, timeoutMillis);
    }

    public void setExpectedSilenceMillis(long timeoutMillis) {
        setSilenceTimeoutMillis(timeoutMillis);
    }

    public synchronized void connect(final BluetoothAdapter adapter, final BluetoothDevice device) {
        disconnect();
        final long connectionGeneration = ++generation;
        running = true;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runConnection(adapter, device, connectionGeneration);
            }
        }, "sds011-bluetooth");
        worker.start();
    }

    public synchronized void disconnect() {
        running = false;
        generation += 1;
        closeSocket(socket);
        Thread currentWorker = worker;
        worker = null;
        if (currentWorker != null) {
            currentWorker.interrupt();
        }
    }

    public synchronized void write(byte[] bytes) {
        if (output == null || bytes == null) {
            return;
        }
        try {
            output.write(bytes);
            output.flush();
            updateSleepState(bytes);
        } catch (IOException ignored) {
            closeSocket(socket);
        }
    }

    private void updateSleepState(byte[] bytes) {
        // SDS011 command frame: AA B4 06 01 <value> ... AB; value 0=sleep, 1=work.
        if (bytes.length < 19 || (bytes[0] & 0xFF) != 0xAA
                || (bytes[1] & 0xFF) != 0xB4 || (bytes[2] & 0xFF) != 0x06
                || (bytes[3] & 0xFF) != 0x01 || (bytes[18] & 0xFF) != 0xAB) {
            return;
        }
        boolean goingToSleep = (bytes[4] & 0xFF) == 0;
        sensorSleeping = goingToSleep;
        if (!goingToSleep) {
            // Allow the fan and sensor to restart after an intentional sleep.
            lastFrameAtMillis = android.os.SystemClock.elapsedRealtime();
            silenceTimedOut = false;
        }
    }

    private void runConnection(BluetoothAdapter adapter, BluetoothDevice device,
                               long connectionGeneration) {
        long retryDelaySeconds = 2L;
        while (isActive(connectionGeneration)) {
            BluetoothSocket localSocket = null;
            String message = "sensor_connection_interrupted";
            boolean permissionLost = false;
            try {
                if (adapter != null) {
                    try {
                        adapter.cancelDiscovery();
                    } catch (Throwable ignored) {
                        // cancelDiscovery kann auf Android 12+ ohne BLUETOOTH_SCAN eine SecurityException werfen.
                    }
                }
                localSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                synchronized (this) {
                    if (!isActive(connectionGeneration)) {
                        closeSocket(localSocket);
                        return;
                    }
                    socket = localSocket;
                }
                localSocket.connect();
                decoder.reset();
                lastFrameAtMillis = android.os.SystemClock.elapsedRealtime();
                silenceTimedOut = false;
                startSilenceMonitor(localSocket, connectionGeneration);
                synchronized (this) {
                    if (!isActive(connectionGeneration)) {
                        closeSocket(localSocket);
                        return;
                    }
                    output = localSocket.getOutputStream();
                }
                listener.onConnected();

                InputStream input = localSocket.getInputStream();
                byte[] buf = new byte[Sds011Protocol.READ_SIZE];
                while (isActive(connectionGeneration)) {
                    int n = input.read(buf);
                    if (n < 0) {
                        message = "sensor_disconnected";
                        break;
                    }
                    List<Reading> readings = decoder.feed(buf, n);
                    if (!readings.isEmpty()) {
                        lastFrameAtMillis = android.os.SystemClock.elapsedRealtime();
                        retryDelaySeconds = 2L;
                    }
                    for (Reading reading : readings) {
                        listener.onReading(reading);
                    }
                }
            } catch (IOException exc) {
                message = formatIOException(exc, silenceTimedOut);
            } catch (SecurityException exc) {
                message = "bluetooth_permission_missing";
                permissionLost = true;
            } finally {
                closeSocket(localSocket);
            }

            if (!isActive(connectionGeneration)) {
                return;
            }
            if (permissionLost) {
                synchronized (this) {
                    if (generation == connectionGeneration) {
                        running = false;
                        worker = null;
                    }
                }
                listener.onDisconnected(message);
                return;
            }
            listener.onDisconnected(message + "; retry in "
                    + retryDelaySeconds + " s");
            try {
                Thread.sleep(retryDelaySeconds * 1000L);
            } catch (InterruptedException exc) {
                if (!isActive(connectionGeneration)) {
                    return;
                }
            }
            retryDelaySeconds = Math.min(retryDelaySeconds * 2L, 60L);
        }
    }

    private synchronized boolean isActive(long connectionGeneration) {
        return running && generation == connectionGeneration;
    }

    private void startSilenceMonitor(final BluetoothSocket monitoredSocket,
                                     final long connectionGeneration) {
        Thread monitor = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isActive(connectionGeneration)) {
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException exc) {
                        return;
                    }
                    if (monitoredSocket != socket) {
                        return;
                    }
                    if (android.os.SystemClock.elapsedRealtime() - lastFrameAtMillis
                            > silenceTimeoutMillis && !sensorSleeping) {
                        silenceTimedOut = true;
                        closeSocket(monitoredSocket);
                        return;
                    }
                }
            }
        }, "sds011-silence-watchdog");
        monitor.setDaemon(true);
        monitor.start();
    }

    private synchronized void closeSocket(BluetoothSocket target) {
        if (target == null) {
            return;
        }
        try {
            target.close();
        } catch (IOException ignored) {
            // Verbindung wird gerade beendet.
        }
        if (socket == target) {
            socket = null;
            output = null;
        }
    }

    private static String formatIOException(IOException exc, boolean silenceTimedOut) {
        if (silenceTimedOut) {
            return "sensor_data_timeout";
        }
        if (exc == null || exc.getMessage() == null) {
            return "sensor_connection_aborted";
        }
        String raw = exc.getMessage().toLowerCase();
        if (raw.contains("read failed") || raw.contains("socket might closed") || raw.contains("read ret -1") || raw.contains("socket closed")) {
            return "sensor_connection_interrupted";
        }
        if (raw.contains("connection refused") || raw.contains("connection reset") || raw.contains("broken pipe")) {
            return "sensor_unresponsive";
        }
        if (raw.contains("timeout") || raw.contains("timed out")) {
            return "connection_timeout";
        }
        return "sensor_connection_interrupted";
    }
}
