package de.demogorgi.sds011native.sds;

import java.util.Arrays;

public final class Sds011Protocol {
    public static final int FRAME_LEN = 10;
    public static final int READ_SIZE = 64;

    private static final int HEAD = 0xAA;
    private static final int COMMANDER_NO = 0xC0;
    private static final int COMMAND = 0xB4;
    private static final int TAIL = 0xAB;
    private static final int CMD_SLEEP = 0x06;
    private static final int CMD_WORK_PERIOD = 0x08;

    private Sds011Protocol() {
    }

    public static Reading parseFrame(byte[] frame) {
        if (frame == null || frame.length != FRAME_LEN) {
            return null;
        }
        if (u(frame[0]) != HEAD || u(frame[1]) != COMMANDER_NO || u(frame[9]) != TAIL) {
            return null;
        }
        byte[] payload = Arrays.copyOfRange(frame, 2, 8);
        if (checksum(payload) != u(frame[8])) {
            return null;
        }

        int raw25 = u(frame[2]) | (u(frame[3]) << 8);
        int raw10 = u(frame[4]) | (u(frame[5]) << 8);
        int deviceId = (u(frame[6]) << 8) | u(frame[7]);
        return new Reading(raw25 / 10.0, raw10 / 10.0, deviceId);
    }

    public static byte[] buildSleepCommand(boolean sleeping) {
        return buildCommand(CMD_SLEEP, true, sleeping ? 0 : 1, 0xFFFF);
    }

    public static byte[] buildWorkPeriodCommand(int minutes) {
        if (minutes < 0 || minutes > 30) {
            throw new IllegalArgumentException("working period must be between 0 and 30 minutes");
        }
        return buildCommand(CMD_WORK_PERIOD, true, minutes, 0xFFFF);
    }

    static int checksum(byte[] payload) {
        int sum = 0;
        for (byte b : payload) {
            sum += u(b);
        }
        return sum & 0xFF;
    }

    private static byte[] buildCommand(int command, boolean write, int value, int deviceId) {
        byte[] payload = new byte[15];
        payload[0] = (byte) (command & 0xFF);
        payload[1] = (byte) (write ? 1 : 0);
        payload[2] = (byte) (value & 0xFF);
        payload[13] = (byte) ((deviceId >> 8) & 0xFF);
        payload[14] = (byte) (deviceId & 0xFF);

        byte[] frame = new byte[19];
        frame[0] = (byte) HEAD;
        frame[1] = (byte) COMMAND;
        System.arraycopy(payload, 0, frame, 2, payload.length);
        frame[17] = (byte) checksum(payload);
        frame[18] = (byte) TAIL;
        return frame;
    }

    private static int u(byte b) {
        return b & 0xFF;
    }
}
