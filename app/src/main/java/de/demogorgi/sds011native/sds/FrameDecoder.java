package de.demogorgi.sds011native.sds;

import java.util.ArrayList;
import java.util.List;

public final class FrameDecoder {
    private static final int HEAD = 0xAA;
    private static final int MAX_BUFFER = 1024;

    private final ArrayList<Byte> buffer = new ArrayList<>();

    public List<Reading> feed(byte[] chunk, int length) {
        if (chunk != null && length > 0) {
            for (int i = 0; i < length; i++) {
                buffer.add(chunk[i]);
            }
        }
        if (buffer.size() > MAX_BUFFER) {
            buffer.subList(0, buffer.size() - MAX_BUFFER).clear();
        }

        ArrayList<Reading> readings = new ArrayList<>();
        while (true) {
            int start = findHead();
            if (start < 0) {
                buffer.clear();
                break;
            }
            if (start > 0) {
                buffer.subList(0, start).clear();
            }
            if (buffer.size() < Sds011Protocol.FRAME_LEN) {
                break;
            }
            byte[] frame = new byte[Sds011Protocol.FRAME_LEN];
            for (int i = 0; i < frame.length; i++) {
                frame[i] = buffer.get(i);
            }
            Reading reading = Sds011Protocol.parseFrame(frame);
            if (reading == null) {
                buffer.remove(0);
            } else {
                buffer.subList(0, Sds011Protocol.FRAME_LEN).clear();
                readings.add(reading);
            }
        }
        return readings;
    }

    private int findHead() {
        for (int i = 0; i < buffer.size(); i++) {
            if ((buffer.get(i) & 0xFF) == HEAD) {
                return i;
            }
        }
        return -1;
    }
}
