package com.droneanalytics.ingestion.parser;

import com.droneanalytics.ingestion.model.DroneType;
import com.droneanalytics.ingestion.model.TelemetryPoint;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses PX4 ULog binary (.ulg) flight logs.
 *
 * ULog format overview:
 *   Header (16 bytes):
 *     [0-6]   Magic: "ULog\x01\x12\x35"
 *     [7]     Version (uint8, currently 1)
 *     [8-15]  Start timestamp (uint64 LE, microseconds)
 *
 *   Messages (after header):
 *     [0-1]   msg_size (uint16 LE) — payload size, excludes this 3-byte header
 *     [2]     msg_type (char)
 *     [3..]   payload (msg_size bytes)
 *
 *   Message types:
 *     'F' (0x46) — Format definition: "topic_name:field_type field_name;..."
 *     'A' (0x41) — Subscription (add_logged_msg): msg_id(uint16) + multi_id(uint8) + name
 *     'D' (0x44) — Logged data: msg_id(uint16) + data bytes
 *     'I' (0x49) — Info message (ignored for telemetry)
 *     'P' (0x50) — Parameter (ignored for telemetry)
 *     'L' (0x4C) — Log string (ignored)
 *     'O' (0x4F) — Dropout notification (ignored)
 *     'S' (0x53) — Sync marker (ignored)
 *     'B' (0x42) — Flag bits (ignored)
 *
 * Topics consumed:
 *   vehicle_gps_position   — lat, lon, alt, fix_type
 *   vehicle_local_position — vx, vy, vz, z (AGL, NED so negate)
 *   vehicle_attitude       — q[4] quaternion (w,x,y,z) → roll/pitch/yaw
 *   battery_status         — voltage_v, remaining
 */
public class Px4UlogParser implements LogParser {

    private static final byte[] ULOG_MAGIC = {0x55, 0x4C, 0x6F, 0x67, 0x01, 0x12, 0x35};
    private static final int HEADER_SIZE = 16;

    @Override
    public boolean canParse(byte[] fileHeader, String filename) {
        return FormatDetector.startsWithBytes(fileHeader, FormatDetector.getUlogMagic());
    }

    @Override
    public List<TelemetryPoint> parse(byte[] fileData) throws ParseException {
        if (fileData.length < HEADER_SIZE) {
            throw new ParseException("ULog file too short");
        }
        ByteBuffer buf = ByteBuffer.wrap(fileData).order(ByteOrder.LITTLE_ENDIAN);

        // Read start timestamp from header
        buf.position(8);
        long startTimestampUs = buf.getLong();

        // --- Message type maps ---
        // format name → field definitions
        Map<String, List<UlogField>> formatMap = new HashMap<>();
        // subscription id → topic name
        Map<Integer, String> subscriptions = new HashMap<>();

        // Accumulated state
        double lat = 0, lon = 0, altMsl = 0, altAgl = 0;
        double vx = 0, vy = 0, vz = 0;
        double roll = 0, pitch = 0, yaw = 0;
        double battV = 0, battRemaining = 0;
        boolean hasGpsFix = false;
        long timestampUs = startTimestampUs;

        List<TelemetryPoint> points = new ArrayList<>();
        buf.position(HEADER_SIZE);

        while (buf.remaining() >= 3) {
            int msgSize = buf.getShort() & 0xFFFF;
            int msgType = buf.get() & 0xFF;

            if (buf.remaining() < msgSize) break;
            int payloadStart = buf.position();

            switch ((char) msgType) {
                case 'F' -> {
                    // Format definition
                    byte[] payload = new byte[msgSize];
                    buf.get(payload);
                    String s = new String(payload, StandardCharsets.US_ASCII);
                    int colon = s.indexOf(':');
                    if (colon > 0) {
                        String name = s.substring(0, colon).trim();
                        String fields = s.substring(colon + 1);
                        formatMap.put(name, parseFormatFields(fields));
                    }
                }
                case 'A' -> {
                    // Subscription
                    if (msgSize >= 3) {
                        int subId = buf.getShort() & 0xFFFF;
                        buf.get(); // multi_id
                        int nameLen = msgSize - 3;
                        byte[] nameBytes = new byte[nameLen];
                        buf.get(nameBytes);
                        String topicName = new String(nameBytes, StandardCharsets.US_ASCII)
                                           .replace("\0", "").trim();
                        subscriptions.put(subId, topicName);
                    } else {
                        buf.position(payloadStart + msgSize);
                    }
                }
                case 'D' -> {
                    // Logged data
                    if (msgSize >= 2) {
                        int subId = buf.getShort() & 0xFFFF;
                        int dataLen = msgSize - 2;
                        byte[] data = new byte[dataLen];
                        buf.get(data);
                        String topicName = subscriptions.get(subId);
                        List<UlogField> fields = (topicName != null) ? formatMap.get(topicName) : null;
                        if (fields != null && topicName != null) {
                            Map<String, Object> vals = decodeData(data, fields);
                            long ts = toLong(vals.get("timestamp"));
                            if (ts > 0) timestampUs = ts;

                            switch (topicName) {
                                case "vehicle_gps_position" -> {
                                    lat = toDouble(vals.get("lat")) * 1e-7;
                                    lon = toDouble(vals.get("lon")) * 1e-7;
                                    altMsl = toDouble(vals.get("alt")) * 1e-3; // mm → m
                                    int fixType = toInt(vals.get("fix_type"));
                                    hasGpsFix = fixType >= 3;
                                    // Emit a point on each GPS update
                                    if (lat != 0 || lon != 0) {
                                        points.add(buildPoint(timestampUs, lat, lon, altMsl, altAgl,
                                            vx, vy, vz, roll, pitch, yaw, battV, battRemaining * 100, hasGpsFix));
                                    }
                                }
                                case "vehicle_local_position" -> {
                                    vx = toDouble(vals.get("vx"));
                                    vy = toDouble(vals.get("vy"));
                                    vz = toDouble(vals.get("vz"));
                                    // NED z is negative up: AGL = -z
                                    altAgl = -toDouble(vals.get("z"));
                                }
                                case "vehicle_attitude" -> {
                                    // Quaternion stored as q[0..3] = [w, x, y, z]
                                    double qw = toDouble(vals.get("q[0]"));
                                    double qx = toDouble(vals.get("q[1]"));
                                    double qy = toDouble(vals.get("q[2]"));
                                    double qz = toDouble(vals.get("q[3]"));
                                    double[] euler = quaternionToEuler(qw, qx, qy, qz);
                                    roll  = Math.toDegrees(euler[0]);
                                    pitch = Math.toDegrees(euler[1]);
                                    yaw   = Math.toDegrees(euler[2]);
                                    if (yaw < 0) yaw += 360;
                                }
                                case "battery_status" -> {
                                    battV = toDouble(vals.get("voltage_v"));
                                    battRemaining = toDouble(vals.get("remaining")); // 0-1
                                }
                            }
                        }
                    } else {
                        buf.position(payloadStart + msgSize);
                    }
                }
                default -> buf.position(payloadStart + msgSize);
            }
        }

        return points;
    }

    @Override
    public DroneType getDroneType() {
        return DroneType.PX4;
    }

    // -------------------------------------------------------------------------
    // ULog format field parsing
    // -------------------------------------------------------------------------

    private static class UlogField {
        String type;   // e.g. "float", "uint64_t", "float[4]"
        String name;   // e.g. "timestamp", "q"
        int arrayLen;  // 1 for scalars, N for arrays
    }

    private List<UlogField> parseFormatFields(String fieldStr) {
        List<UlogField> fields = new ArrayList<>();
        for (String part : fieldStr.split(";")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            int space = part.lastIndexOf(' ');
            if (space < 0) continue;
            UlogField f = new UlogField();
            f.type = part.substring(0, space).trim();
            f.name = part.substring(space + 1).trim();
            // Detect array e.g. "float[4]" → type="float", arrayLen=4, name="q"
            int bracket = f.type.indexOf('[');
            if (bracket >= 0) {
                int closeBracket = f.type.indexOf(']');
                f.arrayLen = Integer.parseInt(f.type.substring(bracket + 1, closeBracket));
                f.type = f.type.substring(0, bracket);
            } else {
                f.arrayLen = 1;
            }
            fields.add(f);
        }
        return fields;
    }

    private Map<String, Object> decodeData(byte[] data, List<UlogField> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (UlogField f : fields) {
            if (!bb.hasRemaining()) break;
            for (int i = 0; i < f.arrayLen; i++) {
                if (!bb.hasRemaining()) break;
                String key = f.arrayLen > 1 ? f.name + "[" + i + "]" : f.name;
                try {
                    switch (f.type) {
                        case "int8_t"   -> result.put(key, (int) bb.get());
                        case "uint8_t", "bool" -> result.put(key, bb.get() & 0xFF);
                        case "int16_t"  -> result.put(key, (int) bb.getShort());
                        case "uint16_t" -> result.put(key, bb.getShort() & 0xFFFF);
                        case "int32_t"  -> result.put(key, bb.getInt());
                        case "uint32_t" -> result.put(key, Integer.toUnsignedLong(bb.getInt()));
                        case "int64_t"  -> result.put(key, bb.getLong());
                        case "uint64_t" -> result.put(key, bb.getLong());
                        case "float"    -> result.put(key, (double) bb.getFloat());
                        case "double"   -> result.put(key, bb.getDouble());
                        case "char"     -> result.put(key, (char) bb.get());
                        default -> result.put(key, null);
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Math: quaternion [w,x,y,z] → Euler [roll, pitch, yaw] in radians
    // -------------------------------------------------------------------------

    private double[] quaternionToEuler(double w, double x, double y, double z) {
        double roll  = Math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y));
        double sinP  = 2 * (w * y - z * x);
        double pitch = Math.abs(sinP) >= 1 ? Math.copySign(Math.PI / 2, sinP) : Math.asin(sinP);
        double yaw   = Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z));
        return new double[]{roll, pitch, yaw};
    }

    // -------------------------------------------------------------------------
    // Assembly helper
    // -------------------------------------------------------------------------

    private TelemetryPoint buildPoint(long timeUs, double lat, double lon,
                                       double altMsl, double altAgl,
                                       double vx, double vy, double vz,
                                       double roll, double pitch, double yaw,
                                       double battV, double battPct, boolean gpsFix) {
        TelemetryPoint p = new TelemetryPoint();
        p.setTimestampMs(timeUs / 1000);
        p.setLatitude(lat);
        p.setLongitude(lon);
        p.setAltitudeMsl(altMsl);
        p.setAltitudeAgl(altAgl);
        p.setVelocityNorth(vx);
        p.setVelocityEast(vy);
        p.setVelocityDown(vz);
        p.setGroundSpeed(Math.sqrt(vx * vx + vy * vy));
        p.setRollDeg(roll);
        p.setPitchDeg(pitch);
        p.setHeadingDeg(yaw);
        p.setBatteryVoltage(battV);
        p.setBatteryPercent(battPct);
        p.setFlightMode("AUTO");
        p.setDroneType(DroneType.PX4);
        p.setHasGpsFix(gpsFix);
        return p;
    }

    private long toLong(Object v)   { return v instanceof Number n ? n.longValue() : 0L; }
    private double toDouble(Object v){ return v instanceof Number n ? n.doubleValue() : 0.0; }
    private int toInt(Object v)     { return v instanceof Number n ? n.intValue() : 0; }
}
