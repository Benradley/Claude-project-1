package com.droneanalytics.ingestion;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Generates realistic binary/text sample flight log files for testing.
 *
 * All samples represent the same 60-second simulated flight:
 *   - Origin: latitude 37.7749, longitude -122.4194 (San Francisco)
 *   - Climb to 50m AGL over first 15s
 *   - Circular orbit at 50m for 30s
 *   - Descend to 0m over final 15s
 *   - Battery drains from 95% to 80%
 *   - Ground speed ~5 m/s during orbit
 */
public class SampleDataGenerator {

    // Simulation parameters
    private static final double BASE_LAT  = 37.7749;
    private static final double BASE_LON  = -122.4194;
    private static final double BASE_ALT_MSL = 100.0; // home point MSL elevation
    private static final double ORBIT_RADIUS_DEG = 0.0002; // ~22m radius
    private static final int FLIGHT_DURATION_S = 60;
    private static final long BASE_TIME_US = 1_700_000_000_000_000L; // ~Nov 2023

    // -------------------------------------------------------------------------
    // Public entry points
    // -------------------------------------------------------------------------

    public static void generateAll(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        writeDjiTxt(outputDir.resolve("sample_dji.txt"));
        writeArduPilotBin(outputDir.resolve("sample_ardupilot.bin"));
        writePx4Ulog(outputDir.resolve("sample_px4.ulg"));
        writeMavlinkTlog(outputDir.resolve("sample_mavlink.tlog"));
        writeParrotJson(outputDir.resolve("sample_parrot.json"));
        System.out.println("Sample files written to: " + outputDir.toAbsolutePath());
    }

    // -------------------------------------------------------------------------
    // Flight path helpers
    // -------------------------------------------------------------------------

    /** Returns {lat, lon, altAgl, speed, heading} at time t seconds into the flight. */
    static double[] flightState(double t) {
        // Altitude: climb 0-15s, hover 15-45s, descend 45-60s
        double altAgl;
        if (t < 15) {
            altAgl = (t / 15.0) * 50.0;
        } else if (t < 45) {
            altAgl = 50.0;
        } else {
            altAgl = 50.0 * (1.0 - (t - 45.0) / 15.0);
        }

        // Circular orbit during hover phase
        double orbitAngle = (t < 15 || t > 45) ? 0 : 2 * Math.PI * (t - 15) / 30.0;
        double lat = BASE_LAT + ORBIT_RADIUS_DEG * Math.cos(orbitAngle);
        double lon = BASE_LON + ORBIT_RADIUS_DEG * Math.sin(orbitAngle);

        // Heading tangent to the circle
        double heading = (t < 15 || t > 45) ? 0 : Math.toDegrees(orbitAngle + Math.PI / 2) % 360;
        if (heading < 0) heading += 360;

        // Ground speed: 0 during climb/descent, 5 m/s during orbit
        double speed = (t >= 15 && t <= 45) ? 5.0 : 0.5;

        return new double[]{lat, lon, altAgl, speed, heading};
    }

    static double batteryPercent(double t) {
        return 95.0 - (t / FLIGHT_DURATION_S) * 15.0;
    }

    static double batteryVoltage(double t) {
        return 12.5 - (t / FLIGHT_DURATION_S) * 0.8;
    }

    // =========================================================================
    // 1. DJI TXT
    // =========================================================================

    public static void writeDjiTxt(Path outPath) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Collect OSD records for 60 samples (1 Hz)
        ByteArrayOutputStream records = new ByteArrayOutputStream();
        for (int t = 0; t < FLIGHT_DURATION_S; t++) {
            double[] state = flightState(t);
            byte[] osdPayload = buildDjiOsdPayload(t * 1000L, state[0], state[1],
                BASE_ALT_MSL + state[2], state[2], (float) state[3], (float) state[4],
                (float) batteryVoltage(t), (int) batteryPercent(t));
            byte[] scrambled = djiXorEncode(osdPayload, 1);
            records.write(1);                   // record type = OSD
            records.write(scrambled.length);    // payload length
            records.write(scrambled);
            records.write(0xFF);                // terminator
        }
        byte[] recordBytes = records.toByteArray();

        // 100-byte header
        ByteBuffer header = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN);
        header.putLong(recordBytes.length);  // record area byte count
        header.putShort((short) 0);          // details area length (0 = none)
        // rest is zero padding

        baos.write(header.array());
        baos.write(recordBytes);
        Files.write(outPath, baos.toByteArray());
    }

    private static byte[] buildDjiOsdPayload(long flyTimeMs, double lat, double lon,
                                              double altMsl, double altAgl,
                                              float speed, float heading,
                                              float battV, int battPct) {
        byte[] payload = new byte[80];
        ByteBuffer bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt((int)(flyTimeMs & 0xFFFFFFFFL));  // offset 0: flyTime uint32
        bb.putDouble(lat);                           // offset 4: latitude
        bb.putDouble(lon);                           // offset 12: longitude
        bb.putFloat((float) altMsl);                 // offset 20: altitudeMsl
        bb.putFloat((float) altAgl);                 // offset 24: altitudeAgl
        bb.putFloat(speed);                          // offset 28: speed
        bb.putFloat(0.0f);                           // offset 32: zSpeed
        bb.putFloat(0.0f);                           // offset 36: pitch
        bb.putFloat(0.0f);                           // offset 40: roll
        bb.putFloat(heading);                        // offset 44: yaw/heading
        // padding to offset 56
        bb.position(56);
        bb.putFloat(battV);                          // offset 56: batteryVoltage
        payload[60] = (byte)(battPct & 0xFF);        // offset 60: batteryPercent
        payload[74] = 1;                             // offset 74: gpsFix=1
        return payload;
    }

    private static byte[] djiXorEncode(byte[] payload, int recordType) {
        if (payload.length == 0) return payload;
        byte[] result = new byte[payload.length];
        result[0] = payload[0];
        int seed = (payload[0] & 0xFF) ^ recordType;
        byte[] key = new byte[8];
        for (int i = 0; i < 8; i++) {
            seed = (seed * 0x6C + 0x21) & 0xFF;
            key[i] = (byte) seed;
        }
        for (int i = 1; i < payload.length; i++) {
            result[i] = (byte)(payload[i] ^ key[(i - 1) % 8]);
        }
        return result;
    }

    // =========================================================================
    // 2. ArduPilot DataFlash BIN
    // =========================================================================

    public static void writeArduPilotBin(Path outPath) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Write FMT messages first.
        // Length = 3-byte header + sum of payload field sizes.
        // GPS  "QBLLifB": Q(8)+B(1)+L(4)+L(4)+i(4)+f(4)+B(1) = 26 payload → 29 total
        // ATT  "Qffffff": Q(8)+f(4)*6 = 32 payload → 35 total
        // BAT  "Qff":     Q(8)+f(4)*2 = 16 payload → 19 total
        // BARO "Qff":     same as BAT → 19 total
        // MODE "QBB":     Q(8)+B(1)+B(1) = 10 payload → 13 total
        writeFmt(baos, 0x80, 89, "FMT",  "BBnNZ",  "Type,Length,Name,Format,Columns");
        writeFmt(baos, 0x01, 29, "GPS",  "QBLLifB", "TimeUS,Status,Lat,Lng,Alt,Spd,NSats");
        writeFmt(baos, 0x02, 35, "ATT",  "Qffffff","TimeUS,Roll,Pitch,Yaw,DesRoll,DesPitch,DesYaw");
        writeFmt(baos, 0x03, 19, "BAT",  "Qff",    "TimeUS,Volt,Curr");
        writeFmt(baos, 0x04, 19, "BARO", "Qff",    "TimeUS,Alt,Press");
        writeFmt(baos, 0x05, 13, "MODE", "QBB",    "TimeUS,Mode,Rsn");

        // Write GPS + ATT + BAT at 1 Hz for 60 seconds
        for (int t = 0; t < FLIGHT_DURATION_S; t++) {
            final int tFinal = t;  // effectively final copy for lambda capture
            double[] s = flightState(t);
            long timeUs = BASE_TIME_US + (long) t * 1_000_000L;
            final double battV = batteryVoltage(t);

            // GPS message
            writeArduMsg(baos, 0x01, buf -> {
                buf.putLong(timeUs);          // TimeUS (Q)
                buf.put((byte) 3);            // Status GPS 3D fix
                buf.putInt((int)(s[0] * 1e7)); // Lat (L)
                buf.putInt((int)(s[1] * 1e7)); // Lng (L)
                buf.putInt((int)(BASE_ALT_MSL + s[2])); // Alt (i)
                buf.putFloat((float) s[3]);   // Spd (f)
                buf.put((byte) 10);           // NSats (B)
            });

            // ATT message
            writeArduMsg(baos, 0x02, buf -> {
                buf.putLong(timeUs);
                buf.putFloat(0.0f);           // Roll
                buf.putFloat(0.0f);           // Pitch
                buf.putFloat((float) s[4]);   // Yaw/heading
                buf.putFloat(0.0f);           // DesRoll
                buf.putFloat(0.0f);           // DesPitch
                buf.putFloat((float) s[4]);   // DesYaw
            });

            // BAT message
            writeArduMsg(baos, 0x03, buf -> {
                buf.putLong(timeUs);
                buf.putFloat((float) battV);
                buf.putFloat(0.5f);           // Curr
            });

            // BARO message
            writeArduMsg(baos, 0x04, buf -> {
                buf.putLong(timeUs);
                buf.putFloat((float) s[2]);   // AGL from barometer
                buf.putFloat(101325.0f);      // Press
            });

            // MODE message (once at start)
            if (tFinal == 0) {
                writeArduMsg(baos, 0x05, buf -> {
                    buf.putLong(timeUs);
                    buf.put((byte) 5);        // LOITER mode
                    buf.put((byte) 0);        // reason
                });
            }
        }

        Files.write(outPath, baos.toByteArray());
    }

    private static void writeFmt(ByteArrayOutputStream out, int msgType, int length,
                                   String name, String format, String columns) throws IOException {
        // FMT message: header[3] + type(1) + length(1) + name(4) + fmt(16) + cols(64) = 89 bytes total
        out.write(0xA3); out.write(0x95); out.write(0x80);
        out.write(msgType);
        out.write(length);
        out.write(padString(name, 4));
        out.write(padString(format, 16));
        out.write(padString(columns, 64));
    }

    @FunctionalInterface
    interface BufferWriter { void write(ByteBuffer buf) throws IOException; }

    private static void writeArduMsg(ByteArrayOutputStream out, int msgId, BufferWriter writer) throws IOException {
        out.write(0xA3); out.write(0x95); out.write(msgId);
        ByteBuffer buf = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        writer.write(buf);
        out.write(buf.array(), 0, buf.position());
    }

    private static byte[] padString(String s, int len) {
        byte[] result = new byte[len];
        byte[] src = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, result, 0, Math.min(src.length, len));
        return result;
    }

    // =========================================================================
    // 3. PX4 ULog
    // =========================================================================

    public static void writePx4Ulog(Path outPath) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Header
        baos.write(new byte[]{0x55, 0x4C, 0x6F, 0x67, 0x01, 0x12, 0x35}); // magic
        baos.write(1); // version
        writeUint64Le(baos, BASE_TIME_US); // start timestamp

        // Format definitions ('F' messages)
        writeUlogMsg(baos, 'F', "vehicle_gps_position:uint64_t timestamp;int32_t lat;int32_t lon;int32_t alt;uint8_t fix_type;".getBytes(StandardCharsets.US_ASCII));
        writeUlogMsg(baos, 'F', "vehicle_local_position:uint64_t timestamp;float x;float y;float z;float vx;float vy;float vz;".getBytes(StandardCharsets.US_ASCII));
        writeUlogMsg(baos, 'F', "vehicle_attitude:uint64_t timestamp;float[4] q;".getBytes(StandardCharsets.US_ASCII));
        writeUlogMsg(baos, 'F', "battery_status:uint64_t timestamp;float voltage_v;float remaining;".getBytes(StandardCharsets.US_ASCII));

        // Subscriptions ('A' messages): subId(2) + multiId(1) + name
        writeUlogSubscription(baos, 0, "vehicle_gps_position");
        writeUlogSubscription(baos, 1, "vehicle_local_position");
        writeUlogSubscription(baos, 2, "vehicle_attitude");
        writeUlogSubscription(baos, 3, "battery_status");

        // Data messages ('D' messages) at 1 Hz
        for (int t = 0; t < FLIGHT_DURATION_S; t++) {
            double[] s = flightState(t);
            long timeUs = BASE_TIME_US + (long) t * 1_000_000L;

            // vehicle_gps_position
            ByteBuffer gps = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
            gps.putLong(timeUs);
            gps.putInt((int)(s[0] * 1e7));             // lat
            gps.putInt((int)(s[1] * 1e7));             // lon
            gps.putInt((int)((BASE_ALT_MSL + s[2]) * 1000)); // alt mm
            gps.put((byte) 3);                         // fix_type
            writeUlogData(baos, 0, gps.array());

            // vehicle_local_position: z = -altAgl (NED)
            double headRad = Math.toRadians(s[4]);
            ByteBuffer lpos = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
            lpos.putLong(timeUs);
            lpos.putFloat(0.0f); // x
            lpos.putFloat(0.0f); // y
            lpos.putFloat((float)(-s[2])); // z (NED, negative = up)
            lpos.putFloat((float)(s[3] * Math.cos(headRad))); // vx
            lpos.putFloat((float)(s[3] * Math.sin(headRad))); // vy
            lpos.putFloat(0.0f); // vz
            writeUlogData(baos, 1, lpos.array());

            // vehicle_attitude: quaternion from heading yaw only (roll=0, pitch=0)
            double halfYaw = Math.toRadians(s[4]) / 2.0;
            ByteBuffer att = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            att.putLong(timeUs);
            att.putFloat((float) Math.cos(halfYaw)); // q[0] = w
            att.putFloat(0.0f);                      // q[1] = x
            att.putFloat(0.0f);                      // q[2] = y
            att.putFloat((float) Math.sin(halfYaw)); // q[3] = z
            writeUlogData(baos, 2, att.array());

            // battery_status
            ByteBuffer bat = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
            bat.putLong(timeUs);
            bat.putFloat((float) batteryVoltage(t));
            bat.putFloat((float)(batteryPercent(t) / 100.0));
            writeUlogData(baos, 3, bat.array());
        }

        Files.write(outPath, baos.toByteArray());
    }

    private static void writeUlogMsg(ByteArrayOutputStream out, char type, byte[] payload) throws IOException {
        ByteBuffer hdr = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
        hdr.putShort((short) payload.length);
        hdr.put((byte) type);
        out.write(hdr.array());
        out.write(payload);
    }

    private static void writeUlogSubscription(ByteArrayOutputStream out, int subId, String name) throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        byte[] payload = new byte[3 + nameBytes.length];
        ByteBuffer bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort((short) subId);
        bb.put((byte) 0); // multi_id
        System.arraycopy(nameBytes, 0, payload, 3, nameBytes.length);
        writeUlogMsg(out, 'A', payload);
    }

    private static void writeUlogData(ByteArrayOutputStream out, int subId, byte[] data) throws IOException {
        byte[] payload = new byte[2 + data.length];
        ByteBuffer bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        bb.putShort((short) subId);
        System.arraycopy(data, 0, payload, 2, data.length);
        writeUlogMsg(out, 'D', payload);
    }

    private static void writeUint64Le(ByteArrayOutputStream out, long value) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        bb.putLong(value);
        out.write(bb.array());
    }

    // =========================================================================
    // 4. MAVLink TLOG (v1)
    // =========================================================================

    public static void writeMavlinkTlog(Path outPath) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte seq = 0;

        for (int t = 0; t < FLIGHT_DURATION_S; t++) {
            double[] s = flightState(t);
            long timeUs = BASE_TIME_US + (long) t * 1_000_000L;

            // GLOBAL_POSITION_INT (msg 33), 28-byte payload
            ByteBuffer gpi = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
            gpi.putInt(t * 1000);                         // time_boot_ms
            gpi.putInt((int)(s[0] * 1e7));                // lat
            gpi.putInt((int)(s[1] * 1e7));                // lon
            gpi.putInt((int)((BASE_ALT_MSL + s[2]) * 1000)); // alt mm MSL
            gpi.putInt((int)(s[2] * 1000));               // relative_alt mm AGL
            gpi.putShort((short)(s[3] * Math.cos(Math.toRadians(s[4])) * 100)); // vx cm/s
            gpi.putShort((short)(s[3] * Math.sin(Math.toRadians(s[4])) * 100)); // vy cm/s
            gpi.putShort((short) 0);                      // vz
            gpi.putShort((short)(s[4] * 100));            // hdg centidegrees
            writeTlogPacket(baos, timeUs, (byte) 33, seq++, gpi.array());

            // BATTERY_STATUS (msg 147)
            // Layout: current_consumed(4) + energy_consumed(4) + temperature(2)
            //       + voltage[10](20) + current_battery(2) + battery_id(1)
            //       + battery_function(1) + type(1) + battery_remaining(1) = 36 bytes
            ByteBuffer bat = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
            bat.putInt(0);                                     // current_consumed mAh
            bat.putInt(0);                                     // energy_consumed hJ
            bat.putShort((short) 2500);                        // temperature centidegrees
            bat.putShort((short)(batteryVoltage(t) * 1000));   // voltage_cell_1 mV
            for (int i = 1; i < 10; i++) bat.putShort((short) 0xFFFF); // cells 2-10 (not present)
            bat.putShort((short) -1);                          // current_battery mA
            bat.put((byte) 0);                                 // battery_id
            bat.put((byte) 0);                                 // battery_function
            bat.put((byte) 0);                                 // type
            bat.put((byte)(int) batteryPercent(t));            // battery_remaining %
            writeTlogPacket(baos, timeUs, (byte) 147, seq++, bat.array());
        }

        Files.write(outPath, baos.toByteArray());
    }

    private static void writeTlogPacket(ByteArrayOutputStream out, long timestampUs,
                                         byte msgId, byte seq, byte[] payload) throws IOException {
        // 8-byte big-endian timestamp
        ByteBuffer ts = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        ts.putLong(timestampUs);
        out.write(ts.array());

        // MAVLink v1 packet
        out.write(0xFE);            // STX
        out.write(payload.length);  // len
        out.write(seq);             // seq
        out.write(1);               // sysid
        out.write(1);               // compid
        out.write(msgId);           // msgid
        out.write(payload);
        // CRC (simplified — not cryptographically correct but structurally valid for our parser)
        int crc = computeMavlinkCrc(payload, msgId);
        out.write(crc & 0xFF);
        out.write((crc >> 8) & 0xFF);
    }

    /** CRC-16/MCRF4XX over payload bytes (simplified). */
    private static int computeMavlinkCrc(byte[] payload, byte msgId) {
        int crc = 0xFFFF;
        for (byte b : payload) {
            crc = crcAccumulate(b, crc);
        }
        return crc;
    }

    private static int crcAccumulate(byte b, int crc) {
        int tmp = (b ^ (crc & 0xFF)) & 0xFF;
        tmp ^= (tmp << 4) & 0xFF;
        return ((crc >> 8) & 0xFF) ^ (tmp << 8) ^ (tmp << 3) ^ (tmp >> 4);
    }

    // =========================================================================
    // 5. Parrot JSON
    // =========================================================================

    public static void writeParrotJson(Path outPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"product\": {\"model\": \"Parrot ANAFI\", \"serial\": \"SAMPLE-0001\"},\n");

        // 1Hz battery data
        sb.append("  \"datas_1Hz\": [\n");
        for (int t = 0; t < FLIGHT_DURATION_S; t++) {
            sb.append("    {");
            sb.append("\"product_battery_voltage\": ").append(String.format("%.3f", batteryVoltage(t))).append(", ");
            sb.append("\"product_battery_remaining_percentage\": ").append((int) batteryPercent(t));
            sb.append("}");
            if (t < FLIGHT_DURATION_S - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // 10Hz position/attitude data
        sb.append("  \"datas_10Hz\": [\n");
        int samples10Hz = FLIGHT_DURATION_S * 10;
        for (int i = 0; i < samples10Hz; i++) {
            double t = i * 0.1;
            double[] s = flightState(t);
            double headRad = Math.toRadians(s[4]);
            sb.append("    {");
            sb.append("\"product_gps_latitude\": ").append(String.format("%.7f", s[0])).append(", ");
            sb.append("\"product_gps_longitude\": ").append(String.format("%.7f", s[1])).append(", ");
            sb.append("\"product_gps_altitude\": ").append(String.format("%.2f", BASE_ALT_MSL + s[2])).append(", ");
            sb.append("\"product_altitude\": ").append(String.format("%.2f", s[2])).append(", ");
            sb.append("\"product_speed_vx\": ").append(String.format("%.3f", s[3] * Math.cos(headRad))).append(", ");
            sb.append("\"product_speed_vy\": ").append(String.format("%.3f", s[3] * Math.sin(headRad))).append(", ");
            sb.append("\"product_speed_vz\": 0.0, ");
            sb.append("\"product_attitude_roll\": 0.0, ");
            sb.append("\"product_attitude_pitch\": 0.0, ");
            sb.append("\"product_attitude_yaw\": ").append(String.format("%.2f", s[4])).append(", ");
            sb.append("\"product_gps_sv_number\": 10");
            sb.append("}");
            if (i < samples10Hz - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");

        Files.writeString(outPath, sb.toString(), StandardCharsets.UTF_8);
    }
}
