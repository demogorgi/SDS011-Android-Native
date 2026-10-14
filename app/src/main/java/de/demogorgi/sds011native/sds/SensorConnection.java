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
        } catch (IOException ignored) {
            closeSocket(socket);
        }
    }

    private void runConnection(BluetoothAdapter adapter, BluetoothDevice device,
                               long connectionGeneration) {
        long retryDelaySeconds = 2L;
        while (isActive(connectionGeneration)) {
            BluetoothSocket localSocket = null;
            String message = "Bluetooth-Fehler";
            boolean permissionLost = false;
            try {
                if (adapter != null) {
                    adapter.cancelDiscovery();
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
                        message = "Sensor getrennt";
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
                message = silenceTimedOut ? "Keine Sensordaten innerhalb des Zeitlimits"
                        : exc.getMessage() == null ? "Bluetooth-Fehler" : exc.getMessage();
            } catch (SecurityException exc) {
                message = "Bluetooth-Berechtigung fehlt";
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
            listener.onDisconnected(message + "; neuer Versuch in "
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
                            > silenceTimeoutMillis) {
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
}
