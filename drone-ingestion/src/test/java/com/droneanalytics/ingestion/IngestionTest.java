package com.droneanalytics.ingestion;

import com.droneanalytics.ingestion.model.DroneType;
import com.droneanalytics.ingestion.model.TelemetryPoint;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests: generate a sample file for each format, ingest it,
 * and assert the normalised TelemetryPoints have valid data.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IngestionTest {

    private static final Path RESOURCES = Path.of("src/test/resources");
    private static FlightLogIngestionService service;

    @BeforeAll
    static void setup() throws Exception {
        service = new FlightLogIngestionService();
        SampleDataGenerator.generateAll(RESOURCES);
    }

    // -------------------------------------------------------------------------
    // DJI
    // -------------------------------------------------------------------------

    @Test @Order(1)
    void testDjiTxtIngestion() throws Exception {
        List<TelemetryPoint> points = service.ingest(RESOURCES.resolve("sample_dji.txt"));

        assertFalse(points.isEmpty(), "DJI: should produce at least one TelemetryPoint");
        assertEquals(DroneType.DJI, points.get(0).getDroneType());

        TelemetryPoint first = points.get(0);
        assertValidCoordinates(first, "DJI");
        assertPositiveBattery(first, "DJI");
        assertTrue(first.getTimestampMs() > 0, "DJI: timestampMs must be positive");
    }

    // -------------------------------------------------------------------------
    // ArduPilot
    // -------------------------------------------------------------------------

    @Test @Order(2)
    void testArduPilotBinIngestion() throws Exception {
        List<TelemetryPoint> points = service.ingest(RESOURCES.resolve("sample_ardupilot.bin"));

        assertFalse(points.isEmpty(), "ArduPilot: should produce at least one TelemetryPoint");
        assertEquals(DroneType.ARDUPILOT, points.get(0).getDroneType());

        TelemetryPoint mid = points.get(points.size() / 2);
        assertValidCoordinates(mid, "ArduPilot");
        assertTrue(mid.getAltitudeMsl() > 0, "ArduPilot: altitudeMsl must be > 0");
        assertTrue(mid.getTimestampMs() > 0, "ArduPilot: timestampMs must be positive");
    }

    // -------------------------------------------------------------------------
    // PX4
    // -------------------------------------------------------------------------

    @Test @Order(3)
    void testPx4UlogIngestion() throws Exception {
        List<TelemetryPoint> points = service.ingest(RESOURCES.resolve("sample_px4.ulg"));

        assertFalse(points.isEmpty(), "PX4: should produce at least one TelemetryPoint");
        assertEquals(DroneType.PX4, points.get(0).getDroneType());

        TelemetryPoint mid = points.get(points.size() / 2);
        assertValidCoordinates(mid, "PX4");
        assertTrue(mid.getAltitudeAgl() >= 0, "PX4: altitudeAgl must be >= 0");
        assertTrue(mid.getBatteryVoltage() > 0, "PX4: batteryVoltage must be > 0");
    }

    // -------------------------------------------------------------------------
    // MAVLink
    // -------------------------------------------------------------------------

    @Test @Order(4)
    void testMavlinkTlogIngestion() throws Exception {
        List<TelemetryPoint> points = service.ingest(RESOURCES.resolve("sample_mavlink.tlog"));

        assertFalse(points.isEmpty(), "MAVLink: should produce at least one TelemetryPoint");
        assertEquals(DroneType.MAVLINK, points.get(0).getDroneType());

        TelemetryPoint first = points.get(0);
        assertValidCoordinates(first, "MAVLink");
        assertTrue(first.getAltitudeAgl() >= 0, "MAVLink: altitudeAgl must be >= 0");
        assertTrue(first.getTimestampMs() > 0, "MAVLink: timestampMs must be positive");
    }

    // -------------------------------------------------------------------------
    // Parrot
    // -------------------------------------------------------------------------

    @Test @Order(5)
    void testParrotJsonIngestion() throws Exception {
        List<TelemetryPoint> points = service.ingest(RESOURCES.resolve("sample_parrot.json"));

        assertFalse(points.isEmpty(), "Parrot: should produce at least one TelemetryPoint");
        assertEquals(DroneType.PARROT, points.get(0).getDroneType());

        TelemetryPoint mid = points.get(points.size() / 2);
        assertValidCoordinates(mid, "Parrot");
        assertTrue(mid.getBatteryPercent() > 0, "Parrot: batteryPercent must be > 0");
        assertTrue(mid.getBatteryVoltage() > 0, "Parrot: batteryVoltage must be > 0");
    }

    // -------------------------------------------------------------------------
    // Format auto-detection
    // -------------------------------------------------------------------------

    @Test @Order(6)
    void testFormatDetection() throws Exception {
        // Verify that FormatDetector picks the correct parser for each file
        // without relying on filename extension alone (except DJI which needs .txt hint).
        String[] files = {"sample_ardupilot.bin", "sample_px4.ulg", "sample_mavlink.tlog", "sample_parrot.json"};
        DroneType[] expected = {DroneType.ARDUPILOT, DroneType.PX4, DroneType.MAVLINK, DroneType.PARROT};

        for (int i = 0; i < files.length; i++) {
            List<TelemetryPoint> pts = service.ingest(RESOURCES.resolve(files[i]));
            assertFalse(pts.isEmpty(), files[i] + " should parse to at least one point");
            assertEquals(expected[i], pts.get(0).getDroneType(), files[i] + " drone type mismatch");
        }
    }

    // -------------------------------------------------------------------------
    // Data integrity: maximum altitude should be ~50m AGL
    // -------------------------------------------------------------------------

    @Test @Order(7)
    void testMaxAltitude() throws Exception {
        String[] files = {"sample_ardupilot.bin", "sample_px4.ulg", "sample_parrot.json"};
        for (String file : files) {
            List<TelemetryPoint> pts = service.ingest(RESOURCES.resolve(file));
            double maxAgl = pts.stream().mapToDouble(TelemetryPoint::getAltitudeAgl).max().orElse(0);
            assertTrue(maxAgl >= 40.0 && maxAgl <= 55.0,
                file + ": max AGL altitude should be ~50m, got " + maxAgl);
        }
    }

    // -------------------------------------------------------------------------
    // Assertion helpers
    // -------------------------------------------------------------------------

    private void assertValidCoordinates(TelemetryPoint p, String label) {
        double lat = p.getLatitude();
        double lon = p.getLongitude();
        assertTrue(lat >= -90 && lat <= 90, label + ": latitude " + lat + " out of range");
        assertTrue(lon >= -180 && lon <= 180, label + ": longitude " + lon + " out of range");
        assertFalse(lat == 0.0 && lon == 0.0, label + ": lat/lon should not both be 0.0 (invalid GPS)");
    }

    private void assertPositiveBattery(TelemetryPoint p, String label) {
        assertTrue(p.getBatteryVoltage() > 0 || p.getBatteryPercent() > 0,
            label + ": at least one battery field should be > 0");
    }
}
