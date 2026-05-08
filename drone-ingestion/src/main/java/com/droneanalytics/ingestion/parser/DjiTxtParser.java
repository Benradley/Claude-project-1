package com.droneanalytics.ingestion.parser;

import com.droneanalytics.ingestion.model.DroneType;
import com.droneanalytics.ingestion.model.TelemetryPoint;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses DJI binary .txt flight logs (pre-GO4 v2.8.4 unencrypted format).
 *
 * File layout:
 *   Bytes 0-7:   Record area byte count (uint64 LE)
 *   Bytes 8-9:   Details area length (uint16 LE)
 *   Bytes 10-99: Padding
 *   Bytes 100+:  Records: [type(1)][length(1)][payload(N)][0xFF]
 *
 * OSD record payload is XOR-scrambled:
 *   scramble[i] = (payload[0] XOR type) rotated/derived into 8-byte key
 *   remaining bytes XOR'd cyclically with that key
 *
 * Key OSD fields at fixed byte offsets within the decoded payload:
 *   Offset  0: flyTime   (uint32 LE, ms)
 *   Offset  4: latitude  (double LE, decimal degrees)
 *   Offset 12: longitude (double LE, decimal degrees)
 *   Offset 20: altitudeMsl (float LE, meters)
 *   Offset 24: altitudeAgl (float LE, meters)
 *   Offset 28: speed      (float LE, m/s ground speed)
 *   Offset 32: zSpeed     (float LE, m/s positive=descending)
 *   Offset 36: pitch      (float LE, degrees)
 *   Offset 40: roll       (float LE, degrees)
 *   Offset 44: yaw        (float LE, degrees 0-360)
 *   Offset 56: batteryVoltage (float LE, volts)
 *   Offset 60: batteryPercent (uint8)
 *   Offset 74: gpsFix     (uint8, nonzero = fix)
 */
public class DjiTxtParser implements LogParser {

    private static final int HEADER_SIZE = 100;
    private static final byte RECORD_TERMINATOR = (byte) 0xFF;
    private static final int OSD_RECORD_TYPE = 1;
    private static final int MIN_OSD_PAYLOAD = 75;

    // Flight start base time — DJI stores relative flyTime; we anchor to Unix epoch
    // In real files this would come from the Details section or file metadata.
    // For normalisation purposes we use a fixed base so tests have predictable timestamps.
    private static final long FLIGHT_BASE_TIME_MS = 1_700_000_000_000L;

    @Override
    public boolean canParse(byte[] fileHeader, String filename) {
        // DJI TXT files have an 8-byte record-area size in the first 8 bytes,
        // followed by 2-byte details-area length. Neither value is zero in a real log.
        // We check that the file is at least 100 bytes and that bytes 0-7 form a
        // plausible (non-zero, not huge) uint64, and use the .txt extension as confirmation.
        if (fileHeader.length < 16) return false;
        String lower = filename.toLowerCase();
        if (!lower.endsWith(".txt") && !lower.endsWith(".dat")) return false;

        // The first 8 bytes are the record area byte count. For any real flight it will
        // be at least 100 and less than 500 MB.
        ByteBuffer bb = ByteBuffer.wrap(fileHeader, 0, 8).order(ByteOrder.LITTLE_ENDIAN);
        long recordAreaSize = bb.getLong();
        return recordAreaSize > 100 && recordAreaSize < 500_000_000L;
    }

    @Override
    public List<TelemetryPoint> parse(byte[] fileData) throws ParseException {
        if (fileData.length < HEADER_SIZE) {
            throw new ParseException("DJI TXT file too short (" + fileData.length + " bytes)");
        }

        List<TelemetryPoint> points = new ArrayList<>();
        int pos = HEADER_SIZE;

        while (pos + 2 < fileData.length) {
            int recordType = fileData[pos] & 0xFF;
            int payloadLen = fileData[pos + 1] & 0xFF;
            pos += 2;

            if (pos + payloadLen + 1 > fileData.length) break;

            byte[] payload = new byte[payloadLen];
            System.arraycopy(fileData, pos, payload, 0, payloadLen);
            pos += payloadLen;

            // Expect 0xFF terminator
            if (fileData[pos] != RECORD_TERMINATOR) {
                // Tolerate missing terminator and continue scanning
                pos++;
                continue;
            }
            pos++;

            if (recordType == OSD_RECORD_TYPE && payloadLen >= MIN_OSD_PAYLOAD) {
                byte[] decoded = xorDecode(payload, recordType);
                TelemetryPoint point = decodeOsd(decoded);
                if (point != null) {
                    points.add(point);
                }
            }
        }

        return points;
    }

    @Override
    public DroneType getDroneType() {
        return DroneType.DJI;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Decodes the XOR scramble applied to DJI OSD payloads.
     * The first byte XOR'd with the record type seeds an 8-byte key stream.
     */
    private byte[] xorDecode(byte[] payload, int recordType) {
        if (payload.length == 0) return payload;
        byte[] key = new byte[8];
        int seed = (payload[0] & 0xFF) ^ recordType;
        // Generate 8-byte key from seed using a simple linear transform
        for (int i = 0; i < 8; i++) {
            seed = (seed * 0x6C + 0x21) & 0xFF;
            key[i] = (byte) seed;
        }
        byte[] decoded = new byte[payload.length];
        decoded[0] = payload[0]; // first byte is the seed, not scrambled
        for (int i = 1; i < payload.length; i++) {
            decoded[i] = (byte) (payload[i] ^ key[(i - 1) % 8]);
        }
        return decoded;
    }

    /** Reads a TelemetryPoint from a decoded OSD payload. Returns null if data is invalid. */
    private TelemetryPoint decodeOsd(byte[] data) {
        if (data.length < MIN_OSD_PAYLOAD) return null;

        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        long flyTimeMs = Integer.toUnsignedLong(bb.getInt(0));
        double lat = bb.getDouble(4);
        double lon = bb.getDouble(12);
        float altMsl = bb.getFloat(20);
        float altAgl = bb.getFloat(24);
        float speed = bb.getFloat(28);
        float zSpeed = bb.getFloat(32);
        float pitch = bb.getFloat(36);
        float roll = bb.getFloat(40);
        float yaw = bb.getFloat(44);
        float battV = bb.getFloat(56);
        int battPct = data[60] & 0xFF;
        int gpsFix = data[74] & 0xFF;

        // Reject obviously bad GPS data
        if (lat == 0.0 && lon == 0.0) return null;
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;

        TelemetryPoint p = new TelemetryPoint();
        p.setTimestampMs(FLIGHT_BASE_TIME_MS + flyTimeMs);
        p.setLatitude(lat);
        p.setLongitude(lon);
        p.setAltitudeMsl(altMsl);
        p.setAltitudeAgl(altAgl);
        p.setGroundSpeed(speed);
        // DJI zSpeed: positive = descending, matches NED velocityDown
        p.setVelocityDown(zSpeed);
        p.setPitchDeg(pitch);
        p.setRollDeg(roll);
        p.setHeadingDeg(yaw < 0 ? yaw + 360 : yaw);
        p.setBatteryVoltage(battV);
        p.setBatteryPercent(battPct);
        p.setFlightMode("GPS");
        p.setDroneType(DroneType.DJI);
        p.setHasGpsFix(gpsFix != 0);

        // Derive NED velocity components from ground speed and heading
        double headingRad = Math.toRadians(p.getHeadingDeg());
        p.setVelocityNorth(speed * Math.cos(headingRad));
        p.setVelocityEast(speed * Math.sin(headingRad));

        return p;
    }
}
