package com.droneanalytics.ingestion.parser;

import com.droneanalytics.ingestion.model.DroneType;
import com.droneanalytics.ingestion.model.TelemetryPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Parrot drone JSON flight logs (Anafi, Bebop, etc.).
 *
 * Schema:
 * {
 *   "product": { ... metadata ... },
 *   "datas_1Hz": [
 *     {
 *       "product_battery_voltage": float,
 *       "product_battery_remaining_percentage": int
 *     }, ...
 *   ],
 *   "datas_10Hz": [
 *     {
 *       "product_gps_latitude": double,
 *       "product_gps_longitude": double,
 *       "product_gps_altitude": double,
 *       "product_gps_sv_number": int,
 *       "product_speed_vx": float,
 *       "product_speed_vy": float,
 *       "product_speed_vz": float,
 *       "product_attitude_roll": float,
 *       "product_attitude_pitch": float,
 *       "product_attitude_yaw": float,
 *       "product_altitude": float        (AGL, from barometer)
 *     }, ...
 *   ]
 * }
 *
 * Timestamps are derived from array index and sample rate:
 *   datas_1Hz:  index × 1000ms
 *   datas_10Hz: index × 100ms
 */
public class ParrotJsonParser implements LogParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean canParse(byte[] fileHeader, String filename) {
        if (fileHeader.length == 0) return false;
        // Must start with '{' (JSON object)
        if (fileHeader[0] != '{') return false;
        // Quick string scan for Parrot-specific keys
        String head = new String(fileHeader).toLowerCase();
        return head.contains("datas") || head.contains("product");
    }

    @Override
    public List<TelemetryPoint> parse(byte[] fileData) throws ParseException {
        JsonNode root;
        try {
            root = MAPPER.readTree(fileData);
        } catch (IOException e) {
            throw new ParseException("Failed to parse Parrot JSON: " + e.getMessage(), e);
        }

        List<TelemetryPoint> points = new ArrayList<>();

        // Prefer 10Hz data if present, fall back to 1Hz
        JsonNode highFreqArray = root.path("datas_10Hz");
        JsonNode lowFreqArray  = root.path("datas_1Hz");

        // Build a parallel battery lookup from 1Hz data (1 entry per second)
        double[] battV   = buildBatteryVoltageArray(lowFreqArray);
        double[] battPct = buildBatteryPercentArray(lowFreqArray);

        if (highFreqArray.isArray() && highFreqArray.size() > 0) {
            for (int i = 0; i < highFreqArray.size(); i++) {
                JsonNode sample = highFreqArray.get(i);
                long tsMs = (long) i * 100; // 10Hz → 100ms per sample

                TelemetryPoint p = parseSample(sample, tsMs, DroneType.PARROT);

                // Blend in battery from 1Hz array
                int secIdx = (int)(tsMs / 1000);
                if (battV.length > 0)   p.setBatteryVoltage(battV[Math.min(secIdx, battV.length - 1)]);
                if (battPct.length > 0) p.setBatteryPercent(battPct[Math.min(secIdx, battPct.length - 1)]);

                if (p.getLatitude() != 0 || p.getLongitude() != 0) {
                    points.add(p);
                }
            }
        } else if (lowFreqArray.isArray() && lowFreqArray.size() > 0) {
            for (int i = 0; i < lowFreqArray.size(); i++) {
                JsonNode sample = lowFreqArray.get(i);
                long tsMs = (long) i * 1000; // 1Hz → 1000ms per sample
                TelemetryPoint p = parseSample(sample, tsMs, DroneType.PARROT);
                p.setBatteryVoltage(battV.length > i ? battV[i] : 0);
                p.setBatteryPercent(battPct.length > i ? battPct[i] : 0);
                if (p.getLatitude() != 0 || p.getLongitude() != 0) {
                    points.add(p);
                }
            }
        }

        return points;
    }

    @Override
    public DroneType getDroneType() {
        return DroneType.PARROT;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private TelemetryPoint parseSample(JsonNode n, long tsMs, DroneType type) {
        TelemetryPoint p = new TelemetryPoint();
        p.setTimestampMs(tsMs);
        p.setLatitude(getDouble(n, "product_gps_latitude"));
        p.setLongitude(getDouble(n, "product_gps_longitude"));
        p.setAltitudeMsl(getDouble(n, "product_gps_altitude"));
        p.setAltitudeAgl(getDouble(n, "product_altitude"));

        double vx = getDouble(n, "product_speed_vx"); // north
        double vy = getDouble(n, "product_speed_vy"); // east
        double vz = getDouble(n, "product_speed_vz"); // up (Parrot convention)

        p.setVelocityNorth(vx);
        p.setVelocityEast(vy);
        p.setVelocityDown(-vz); // Parrot uses FLU (up positive), convert to NED (down positive)
        p.setGroundSpeed(Math.sqrt(vx * vx + vy * vy));

        double roll  = getDouble(n, "product_attitude_roll");
        double pitch = getDouble(n, "product_attitude_pitch");
        double yaw   = getDouble(n, "product_attitude_yaw");
        p.setRollDeg(roll);
        p.setPitchDeg(pitch);
        p.setHeadingDeg(yaw < 0 ? yaw + 360 : yaw);

        int svCount = getInt(n, "product_gps_sv_number");
        p.setHasGpsFix(svCount >= 4);
        p.setFlightMode("AUTO");
        p.setDroneType(type);
        return p;
    }

    private double[] buildBatteryVoltageArray(JsonNode array) {
        if (!array.isArray()) return new double[0];
        double[] vals = new double[array.size()];
        for (int i = 0; i < array.size(); i++) {
            vals[i] = getDouble(array.get(i), "product_battery_voltage");
        }
        return vals;
    }

    private double[] buildBatteryPercentArray(JsonNode array) {
        if (!array.isArray()) return new double[0];
        double[] vals = new double[array.size()];
        for (int i = 0; i < array.size(); i++) {
            vals[i] = getDouble(array.get(i), "product_battery_remaining_percentage");
        }
        return vals;
    }

    private double getDouble(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return (v != null && v.isNumber()) ? v.doubleValue() : 0.0;
    }

    private int getInt(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return (v != null && v.isNumber()) ? v.intValue() : 0;
    }
}
