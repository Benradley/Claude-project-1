package com.droneanalytics.ingestion.parser;

import com.droneanalytics.ingestion.model.DroneType;
import com.droneanalytics.ingestion.model.TelemetryPoint;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses ArduPilot DataFlash binary (.bin) flight logs.
 *
 * Format overview:
 *   - Self-describing: FMT messages define all other message schemas.
 *   - Every message starts with 3-byte header: [0xA3][0x95][msgId]
 *   - FMT message (msgId=0x80) defines: type, length, name(4), fmt(16), columns(64)
 *   - Format chars: b=int8, B=uint8, h=int16, H=uint16, i=int32, I=uint32,
 *                   f=float, d=double, q=int64, Q=uint64,
 *                   L=int32*1e-7(lat/lon), c=int16*0.01, C=uint16*0.01,
 *                   e=int32*0.01, E=uint32*0.01, n=char[4], N=char[16], Z=char[64],
 *                   M=uint8(mode), a=int16[32]
 *
 * Key messages used:
 *   GPS  — TimeUS, Status, Lat, Lng, Alt, Spd
 *   ATT  — TimeUS, Roll, Pitch, Yaw
 *   BAT  — TimeUS, Volt, CurrTot
 *   BARO — TimeUS, Alt
 *   MODE — TimeUS, Mode, ModeNum
 */
public class ArduPilotBinParser implements LogParser {

    private static final byte HEAD1 = (byte) 0xA3;
    private static final byte HEAD2 = (byte) 0x95;
    private static final int FMT_MSG_ID = 0x80;
    private static final int FMT_MSG_LENGTH = 89; // fixed length of a FMT message

    // Maps ArduPilot numeric mode IDs to readable strings (Copter subset)
    private static final Map<Integer, String> COPTER_MODES = new HashMap<>();
    static {
        COPTER_MODES.put(0, "STABILIZE"); COPTER_MODES.put(1, "ACRO");
        COPTER_MODES.put(2, "ALT_HOLD");  COPTER_MODES.put(3, "AUTO");
        COPTER_MODES.put(4, "GUIDED");    COPTER_MODES.put(5, "LOITER");
        COPTER_MODES.put(6, "RTL");       COPTER_MODES.put(9, "LAND");
        COPTER_MODES.put(16, "POSHOLD");  COPTER_MODES.put(19, "BRAKE");
    }

    @Override
    public boolean canParse(byte[] fileHeader, String filename) {
        // ArduPilot BIN files start with a FMT message: [0xA3][0x95][0x80]
        if (fileHeader.length < 3) return false;
        return fileHeader[0] == HEAD1 && fileHeader[1] == HEAD2 && (fileHeader[2] & 0xFF) == FMT_MSG_ID;
    }

    @Override
    public List<TelemetryPoint> parse(byte[] fileData) throws ParseException {
        // Pass 1: build the FMT definitions map
        Map<Integer, FmtDefinition> fmtMap = buildFmtMap(fileData);

        // Pass 2: decode messages and assemble TelemetryPoints
        return decodeMessages(fileData, fmtMap);
    }

    @Override
    public DroneType getDroneType() {
        return DroneType.ARDUPILOT;
    }

    // -------------------------------------------------------------------------
    // Inner class: FMT message definition
    // -------------------------------------------------------------------------

    private static class FmtDefinition {
        int msgId;
        int length;        // total record length including 3-byte header
        String name;       // e.g. "GPS", "ATT", "BAT"
        String format;     // e.g. "QBLLifffBHI"
        String[] columns;  // e.g. ["TimeUS","Status","Lat","Lng","Alt","Spd",...]
    }

    // -------------------------------------------------------------------------
    // Pass 1: scan FMT messages
    // -------------------------------------------------------------------------

    private Map<Integer, FmtDefinition> buildFmtMap(byte[] data) {
        Map<Integer, FmtDefinition> map = new HashMap<>();
        int pos = 0;
        while (pos + 3 <= data.length) {
            if ((data[pos] & 0xFF) != (HEAD1 & 0xFF) || (data[pos + 1] & 0xFF) != (HEAD2 & 0xFF)) {
                pos++;
                continue;
            }
            int msgId = data[pos + 2] & 0xFF;
            if (msgId == FMT_MSG_ID) {
                if (pos + FMT_MSG_LENGTH > data.length) break;
                FmtDefinition def = parseFmtMessage(data, pos + 3);
                if (def != null) {
                    map.put(def.msgId, def);
                }
                pos += FMT_MSG_LENGTH;
            } else {
                FmtDefinition def = map.get(msgId);
                if (def != null && def.length > 3) {
                    pos += def.length;
                } else {
                    pos++;
                }
            }
        }
        return map;
    }

    /**
     * Parses a FMT payload starting at dataOffset (after the 3-byte message header).
     * FMT payload layout (86 bytes):
     *   [0]     type (uint8) — the msgId this FMT describes
     *   [1]     length (uint8) — total message length including header
     *   [2-5]   name (char[4])
     *   [6-21]  format (char[16])
     *   [22-85] columns (char[64])
     */
    private FmtDefinition parseFmtMessage(byte[] data, int offset) {
        if (offset + 86 > data.length) return null;
        FmtDefinition def = new FmtDefinition();
        def.msgId = data[offset] & 0xFF;
        def.length = data[offset + 1] & 0xFF;
        def.name = readString(data, offset + 2, 4);
        def.format = readString(data, offset + 6, 16);
        String colStr = readString(data, offset + 22, 64);
        def.columns = colStr.split(",");
        for (int i = 0; i < def.columns.length; i++) {
            def.columns[i] = def.columns[i].trim();
        }
        return def;
    }

    // -------------------------------------------------------------------------
    // Pass 2: decode data messages
    // -------------------------------------------------------------------------

    private List<TelemetryPoint> decodeMessages(byte[] data, Map<Integer, FmtDefinition> fmtMap) {
        // Mutable state accumulated across messages before assembling a point
        long currentTimeUs = 0;
        double lat = 0, lon = 0, altMsl = 0, speed = 0;
        double altAgl = 0;
        double roll = 0, pitch = 0, yaw = 0;
        double battV = 0;
        String flightMode = "UNKNOWN";
        boolean hasGps = false;
        long lastGpsTimeUs = -1;

        List<TelemetryPoint> points = new ArrayList<>();
        int pos = 0;

        while (pos + 3 <= data.length) {
            if ((data[pos] & 0xFF) != (HEAD1 & 0xFF) || (data[pos + 1] & 0xFF) != (HEAD2 & 0xFF)) {
                pos++;
                continue;
            }
            int msgId = data[pos + 2] & 0xFF;
            FmtDefinition def = fmtMap.get(msgId);
            if (def == null || def.length <= 3) { pos++; continue; }

            int payloadOffset = pos + 3;
            int payloadLen = def.length - 3;
            if (payloadOffset + payloadLen > data.length) break;

            Map<String, Object> fields = decodePayload(data, payloadOffset, def);

            switch (def.name.trim()) {
                case "GPS" -> {
                    currentTimeUs = toLong(fields.get("TimeUS"));
                    lat = toDouble(fields.get("Lat"));
                    lon = toDouble(fields.get("Lng"));
                    altMsl = toDouble(fields.get("Alt"));
                    speed = toDouble(fields.get("Spd"));
                    int status = toInt(fields.get("Status"));
                    hasGps = status >= 3;
                    lastGpsTimeUs = currentTimeUs;

                    TelemetryPoint p = buildPoint(currentTimeUs, lat, lon, altMsl, altAgl,
                        speed, roll, pitch, yaw, battV, flightMode, hasGps);
                    if (lat != 0 || lon != 0) points.add(p);
                }
                case "ATT" -> {
                    roll = toDouble(fields.get("Roll"));
                    pitch = toDouble(fields.get("Pitch"));
                    yaw = toDouble(fields.get("Yaw"));
                }
                case "BARO" -> altAgl = toDouble(fields.get("Alt"));
                case "BAT" -> battV = toDouble(fields.get("Volt"));
                case "MODE" -> {
                    int modeNum = toInt(fields.get("Mode"));
                    flightMode = COPTER_MODES.getOrDefault(modeNum, "MODE_" + modeNum);
                }
            }

            pos += def.length;
        }

        return points;
    }

    /**
     * Decodes a message payload according to the format string in the FmtDefinition.
     * Returns a map of column name → typed value.
     */
    private Map<String, Object> decodePayload(byte[] data, int offset, FmtDefinition def) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ByteBuffer bb = ByteBuffer.wrap(data, offset, Math.min(def.length - 3, data.length - offset))
                                  .order(ByteOrder.LITTLE_ENDIAN);

        int colIdx = 0;
        for (char fmtChar : def.format.toCharArray()) {
            if (!bb.hasRemaining()) break;
            String colName = (colIdx < def.columns.length) ? def.columns[colIdx] : "field" + colIdx;
            colIdx++;

            try {
                switch (fmtChar) {
                    case 'b' -> fields.put(colName, (int) bb.get());
                    case 'B', 'M' -> fields.put(colName, bb.get() & 0xFF);
                    case 'h' -> fields.put(colName, (int) bb.getShort());
                    case 'H' -> fields.put(colName, bb.getShort() & 0xFFFF);
                    case 'c' -> fields.put(colName, bb.getShort() * 0.01);
                    case 'C' -> fields.put(colName, (bb.getShort() & 0xFFFF) * 0.01);
                    case 'i' -> fields.put(colName, bb.getInt());
                    case 'I' -> fields.put(colName, Integer.toUnsignedLong(bb.getInt()));
                    case 'e' -> fields.put(colName, bb.getInt() * 0.01);
                    case 'E' -> fields.put(colName, Integer.toUnsignedLong(bb.getInt()) * 0.01);
                    case 'L' -> fields.put(colName, bb.getInt() * 1e-7);  // lat/lon
                    case 'f' -> fields.put(colName, (double) bb.getFloat());
                    case 'd' -> fields.put(colName, bb.getDouble());
                    case 'q' -> fields.put(colName, bb.getLong());
                    case 'Q' -> fields.put(colName, bb.getLong()); // treat as signed for arithmetic
                    case 'n' -> { fields.put(colName, readStringFromBuffer(bb, 4)); colIdx--; colIdx++; }
                    case 'N' -> fields.put(colName, readStringFromBuffer(bb, 16));
                    case 'Z' -> fields.put(colName, readStringFromBuffer(bb, 64));
                    case 'a' -> { skipBytes(bb, 64); fields.put(colName, null); } // int16[32]
                    default -> fields.put(colName, null);
                }
            } catch (Exception e) {
                break; // ran out of data
            }
        }
        return fields;
    }

    // -------------------------------------------------------------------------
    // Assembly helpers
    // -------------------------------------------------------------------------

    private TelemetryPoint buildPoint(long timeUs, double lat, double lon,
                                       double altMsl, double altAgl, double speed,
                                       double roll, double pitch, double yaw,
                                       double battV, String mode, boolean gpsFix) {
        TelemetryPoint p = new TelemetryPoint();
        p.setTimestampMs(timeUs / 1000);
        p.setLatitude(lat);
        p.setLongitude(lon);
        p.setAltitudeMsl(altMsl);
        p.setAltitudeAgl(altAgl);
        p.setGroundSpeed(speed);
        p.setRollDeg(roll);
        p.setPitchDeg(pitch);
        p.setHeadingDeg(yaw < 0 ? yaw + 360 : yaw);
        p.setBatteryVoltage(battV);
        p.setFlightMode(mode);
        p.setDroneType(DroneType.ARDUPILOT);
        p.setHasGpsFix(gpsFix);

        double headingRad = Math.toRadians(p.getHeadingDeg());
        p.setVelocityNorth(speed * Math.cos(headingRad));
        p.setVelocityEast(speed * Math.sin(headingRad));
        return p;
    }

    // -------------------------------------------------------------------------
    // Utility methods
    // -------------------------------------------------------------------------

    private String readString(byte[] data, int offset, int maxLen) {
        int end = offset;
        while (end < offset + maxLen && end < data.length && data[end] != 0) end++;
        return new String(data, offset, end - offset, StandardCharsets.US_ASCII).trim();
    }

    private String readStringFromBuffer(ByteBuffer bb, int len) {
        byte[] buf = new byte[len];
        bb.get(buf);
        int end = 0;
        while (end < len && buf[end] != 0) end++;
        return new String(buf, 0, end, StandardCharsets.US_ASCII).trim();
    }

    private void skipBytes(ByteBuffer bb, int n) {
        int skip = Math.min(n, bb.remaining());
        bb.position(bb.position() + skip);
    }

    private long toLong(Object v) { return v instanceof Number n ? n.longValue() : 0L; }
    private double toDouble(Object v) { return v instanceof Number n ? n.doubleValue() : 0.0; }
    private int toInt(Object v) { return v instanceof Number n ? n.intValue() : 0; }
}
