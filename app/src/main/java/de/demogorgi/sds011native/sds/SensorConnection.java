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

    public SensorConnection(Listener listener) {
        this.listener = listener;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized void connect(final BluetoothAdapter adapter, final BluetoothDevice device) {
        disconnect();
        running = true;
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                runConnection(adapter, device);
            }
        }, "sds011-bluetooth");
        worker.start();
    }

    public synchronized void disconnect() {
        running = false;
        closeSocket();
    }

    public synchronized void write(byte[] bytes) {
        if (output == null || bytes == null) {
            return;
        }
        try {
            output.write(bytes);
            output.flush();
        } catch (IOException ignored) {
            closeSocket();
        }
    }

    private void runConnection(BluetoothAdapter adapter, BluetoothDevice device) {
        String message = "getrennt";
        try {
            if (adapter != null) {
                adapter.cancelDiscovery();
            }
            BluetoothSocket localSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            synchronized (this) {
                socket = localSocket;
            }
            localSocket.connect();
            synchronized (this) {
                output = localSocket.getOutputStream();
            }
            listener.onConnected();

            InputStream input = localSocket.getInputStream();
            byte[] buf = new byte[Sds011Protocol.READ_SIZE];
            while (running) {
                int n = input.read(buf);
                if (n < 0) {
                    message = "Sensor getrennt";
                    break;
                }
                List<Reading> readings = decoder.feed(buf, n);
                for (Reading reading : readings) {
                    listener.onReading(reading);
                }
            }
        } catch (IOException exc) {
            message = exc.getMessage() == null ? "Bluetooth-Fehler" : exc.getMessage();
        } finally {
            synchronized (this) {
                running = false;
                output = null;
            }
            closeSocket();
            listener.onDisconnected(message);
        }
    }

    private synchronized void closeSocket() {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Verbindung wird gerade beendet.
        }
        socket = null;
        output = null;
    }
}
