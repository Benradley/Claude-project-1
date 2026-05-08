package com.droneanalytics.ingestion.parser;

import com.droneanalytics.ingestion.model.DroneType;
import com.droneanalytics.ingestion.model.TelemetryPoint;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses MAVLink ground station telemetry logs (.tlog).
 *
 * TLOG format:
 *   Each entry = 8-byte uint64 timestamp (microseconds, big-endian) + MAVLink packet
 *
 * MAVLink v1 packet:  0xFE | len(1) | seq(1) | sysid(1) | compid(1) | msgid(1) | payload[len] | crc(2)
 * MAVLink v2 packet:  0xFD | len(1) | incompat(1) | compat(1) | seq(1) | sysid(1) | compid(1) | msgid(3 LE) | payload[len] | crc(2) [| sig(13)]
 *
 * Message IDs decoded:
 *   0   HEARTBEAT          — vehicle type, base_mode, custom_mode
 *   33  GLOBAL_POSITION_INT — lat/lon/alt/relative_alt/vx/vy/vz/hdg
 *   74  VFR_HUD             — groundspeed, heading, alt, climb
 *   147 BATTERY_STATUS      — remaining, voltage_cell_1
 */
public class MavlinkTlogParser implements LogParser {

    private static final byte MAVLINK_V1_STX = (byte) 0xFE;
    private static final byte MAVLINK_V2_STX = (byte) 0xFD;

    // MAVLink message IDs
    private static final int MSG_HEARTBEAT            = 0;
    private static final int MSG_GLOBAL_POSITION_INT  = 33;
    private static final int MSG_VFR_HUD              = 74;
    private static final int MSG_BATTERY_STATUS       = 147;

    @Override
    public boolean canParse(byte[] fileHeader, String filename) {
        // TLOG: 8-byte timestamp prefix (all bytes plausible for a unix µs timestamp),
        // followed by MAVLink start byte 0xFE or 0xFD.
        // We check bytes 8 or 9 for the start byte as the timestamp may vary.
        if (fileHeader.length < 10) return false;
        byte b8 = fileHeader[8];
        byte b9 = fileHeader[9];
        return b8 == MAVLINK_V1_STX || b8 == MAVLINK_V2_STX
            || b9 == MAVLINK_V1_STX || b9 == MAVLINK_V2_STX;
    }

    @Override
    public List<TelemetryPoint> parse(byte[] fileData) throws ParseException {
        List<TelemetryPoint> points = new ArrayList<>();
        ByteBuffer buf = ByteBuffer.wrap(fileData).order(ByteOrder.LITTLE_ENDIAN);

        // Accumulated state
        long timestampUs = 0;
        double lat = 0, lon = 0, altMsl = 0, altAgl = 0;
        double vx = 0, vy = 0, vz = 0, heading = 0;
        double groundSpeed = 0;
        double battV = 0;
        int battPct = -1;
        boolean hasGps = false;
        String flightMode = "UNKNOWN";

        while (buf.remaining() >= 9) {
            // 8-byte timestamp (big-endian in the TLOG spec)
            buf.order(ByteOrder.BIG_ENDIAN);
            long ts = buf.getLong();
            buf.order(ByteOrder.LITTLE_ENDIAN);

            if (buf.remaining() < 1) break;
            byte stx = buf.get();

            if (stx == MAVLINK_V1_STX) {
                if (buf.remaining() < 5) break;
                int payloadLen = buf.get() & 0xFF;
                int seq        = buf.get() & 0xFF;
                int sysId      = buf.get() & 0xFF;
                int compId     = buf.get() & 0xFF;
                int msgId      = buf.get() & 0xFF;
                if (buf.remaining() < payloadLen + 2) break;
                byte[] payload = new byte[payloadLen];
                buf.get(payload);
                buf.getShort(); // CRC

                timestampUs = ts;
                switch (msgId) {
                    case MSG_GLOBAL_POSITION_INT -> {
                        lat        = parseGlobalPositionIntLat(payload);
                        lon        = parseGlobalPositionIntLon(payload);
                        altMsl     = parseGlobalPositionIntAltMsl(payload);
                        altAgl     = parseGlobalPositionIntAltAgl(payload);
                        vx         = parseGlobalPositionIntVx(payload);
                        vy         = parseGlobalPositionIntVy(payload);
                        vz         = parseGlobalPositionIntVz(payload);
                        heading    = parseGlobalPositionIntHdg(payload);
                        groundSpeed = Math.sqrt(vx * vx + vy * vy);
                        hasGps = true;
                        if (lat != 0 || lon != 0) {
                            points.add(buildPoint(timestampUs, lat, lon, altMsl, altAgl,
                                vx, vy, vz, groundSpeed, heading, battV, battPct, hasGps, flightMode));
                        }
                    }
                    case MSG_VFR_HUD -> {
                        ByteBuffer pb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
                        if (pb.remaining() >= 16) {
                            groundSpeed = pb.getFloat();   // airspeed (skip — use groundspeed)
                            groundSpeed = pb.getFloat();   // groundspeed
                            heading     = pb.getShort() & 0xFFFF;
                        }
                    }
                    case MSG_BATTERY_STATUS -> {
                        // Layout (36 bytes): current_consumed(4) + energy_consumed(4)
                        //   + temperature(2) + voltage[10](20) + current_battery(2)
                        //   + battery_id(1) + battery_function(1) + type(1) + battery_remaining(1)
                        ByteBuffer pb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
                        if (pb.remaining() >= 36) {
                            pb.getInt();   // current_consumed
                            pb.getInt();   // energy_consumed
                            pb.getShort(); // temperature
                            int cellV = pb.getShort() & 0xFFFF; // voltage_cell_1 mV
                            battV = cellV / 1000.0;
                            // Skip voltage[1..9](18) + current_battery(2) + battery_id(1)
                            //      + battery_function(1) + type(1) = 23 bytes → position 35
                            pb.position(pb.position() + 23);
                            battPct = pb.get(); // battery_remaining at offset 35
                        }
                    }
                    case MSG_HEARTBEAT -> {
                        if (payload.length >= 6) {
                            int customMode = ByteBuffer.wrap(payload, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                            flightMode = "MODE_" + customMode;
                        }
                    }
                }

            } else if (stx == MAVLINK_V2_STX) {
                if (buf.remaining() < 9) break;
                int payloadLen  = buf.get() & 0xFF;
                int incompatFlags = buf.get() & 0xFF;
                int compatFlags   = buf.get() & 0xFF;
                int seq         = buf.get() & 0xFF;
                int sysId       = buf.get() & 0xFF;
                int compId      = buf.get() & 0xFF;
                // 24-bit little-endian message ID
                int msgId = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8) | ((buf.get() & 0xFF) << 16);
                if (buf.remaining() < payloadLen + 2) break;
                byte[] payload = new byte[payloadLen];
                buf.get(payload);
                buf.getShort(); // CRC
                // Skip signature if present
                if ((incompatFlags & 0x01) != 0 && buf.remaining() >= 13) {
                    buf.position(buf.position() + 13);
                }

                timestampUs = ts;
                if (msgId == MSG_GLOBAL_POSITION_INT && payload.length >= 28) {
                    lat     = parseGlobalPositionIntLat(payload);
                    lon     = parseGlobalPositionIntLon(payload);
                    altMsl  = parseGlobalPositionIntAltMsl(payload);
                    altAgl  = parseGlobalPositionIntAltAgl(payload);
                    vx      = parseGlobalPositionIntVx(payload);
                    vy      = parseGlobalPositionIntVy(payload);
                    vz      = parseGlobalPositionIntVz(payload);
                    heading = parseGlobalPositionIntHdg(payload);
                    groundSpeed = Math.sqrt(vx * vx + vy * vy);
                    hasGps = true;
                    if (lat != 0 || lon != 0) {
                        points.add(buildPoint(timestampUs, lat, lon, altMsl, altAgl,
                            vx, vy, vz, groundSpeed, heading, battV, battPct, hasGps, flightMode));
                    }
                }
            } else {
                // Not a valid start byte — scan forward one byte to resync
                buf.position(buf.position() - 1);
                // Skip 1 byte and try again
                if (buf.remaining() > 0) buf.get();
            }
        }

        return points;
    }

    @Override
    public DroneType getDroneType() {
        return DroneType.MAVLINK;
    }

    // -------------------------------------------------------------------------
    // GLOBAL_POSITION_INT (msg 33) payload decoders
    // Payload layout (28 bytes):
    //   [0-3]   time_boot_ms (uint32)
    //   [4-7]   lat (int32, ×1e-7 degrees)
    //   [8-11]  lon (int32, ×1e-7 degrees)
    //   [12-15] alt (int32, mm MSL)
    //   [16-19] relative_alt (int32, mm AGL)
    //   [20-21] vx (int16, cm/s north)
    //   [22-23] vy (int16, cm/s east)
    //   [24-25] vz (int16, cm/s down)
    //   [26-27] hdg (uint16, centidegrees 0-35999)
    // -------------------------------------------------------------------------

    private double parseGlobalPositionIntLat(byte[] p) {
        return ByteBuffer.wrap(p, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() * 1e-7;
    }
    private double parseGlobalPositionIntLon(byte[] p) {
        return ByteBuffer.wrap(p, 8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() * 1e-7;
    }
    private double parseGlobalPositionIntAltMsl(byte[] p) {
        return ByteBuffer.wrap(p, 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() / 1000.0;
    }
    private double parseGlobalPositionIntAltAgl(byte[] p) {
        return ByteBuffer.wrap(p, 16, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() / 1000.0;
    }
    private double parseGlobalPositionIntVx(byte[] p) {
        return ByteBuffer.wrap(p, 20, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() / 100.0;
    }
    private double parseGlobalPositionIntVy(byte[] p) {
        return ByteBuffer.wrap(p, 22, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() / 100.0;
    }
    private double parseGlobalPositionIntVz(byte[] p) {
        return ByteBuffer.wrap(p, 24, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() / 100.0;
    }
    private double parseGlobalPositionIntHdg(byte[] p) {
        return (ByteBuffer.wrap(p, 26, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF) / 100.0;
    }

    // -------------------------------------------------------------------------
    // Assembly helper
    // -------------------------------------------------------------------------

    private TelemetryPoint buildPoint(long timeUs, double lat, double lon,
                                       double altMsl, double altAgl,
                                       double vx, double vy, double vz,
                                       double speed, double heading,
                                       double battV, int battPct,
                                       boolean gpsFix, String mode) {
        TelemetryPoint p = new TelemetryPoint();
        p.setTimestampMs(timeUs / 1000);
        p.setLatitude(lat);
        p.setLongitude(lon);
        p.setAltitudeMsl(altMsl);
        p.setAltitudeAgl(altAgl);
        p.setVelocityNorth(vx);
        p.setVelocityEast(vy);
        p.setVelocityDown(vz);
        p.setGroundSpeed(speed);
        p.setHeadingDeg(heading);
        p.setBatteryVoltage(battV);
        p.setBatteryPercent(battPct >= 0 ? battPct : 0);
        p.setFlightMode(mode);
        p.setDroneType(DroneType.MAVLINK);
        p.setHasGpsFix(gpsFix);
        return p;
    }
}
