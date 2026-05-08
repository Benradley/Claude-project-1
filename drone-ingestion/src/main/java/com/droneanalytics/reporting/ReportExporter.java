package com.droneanalytics.reporting;

import com.droneanalytics.analytics.model.*;
import com.droneanalytics.ingestion.model.TelemetryPoint;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates CSV and PDF reports from an AnalysisReport (FR-5).
 *
 * CSV: exports every TelemetryPoint as a row (suitable for import into Excel / Grafana).
 * PDF: formatted one-page summary with all analytics results.
 */
@Service
public class ReportExporter {

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    // ── CSV ───────────────────────────────────────────────────────────────────

    /**
     * Serialises the AnalysisReport to a UTF-8 CSV byte array.
     *
     * Section 1: flight summary metadata (key=value rows)
     * Section 2: per-point telemetry table with standard column headers
     */
    public byte[] toCsv(AnalysisReport report) throws IOException {
        StringWriter sw = new StringWriter();

        // -- Metadata block ---------------------------------------------------
        sw.write("# Drone Analytics Report\n");
        sw.write("# Generated: " + TS_FMT.format(Instant.now()) + " UTC\n");
        FlightSummary s = report.getSummary();
        if (s != null) {
            sw.write("# DroneType," + report.getDroneType() + "\n");
            sw.write("# Duration(s)," + String.format("%.1f", s.getFlightDurationMs() / 1000.0) + "\n");
            sw.write("# TotalDistance(m)," + String.format("%.1f", s.getTotalDistanceM()) + "\n");
            sw.write("# MaxAGL(m)," + String.format("%.1f", s.getMaxAltitudeAglM()) + "\n");
            sw.write("# BatteryDrain(%)," + String.format("%.1f", s.getBatteryDrainPct()) + "\n");
        }
        PathOptimizationResult p = report.getPathOptimization();
        if (p != null) {
            sw.write("# PathEfficiency(%)," + String.format("%.1f", p.getEfficiencyScore()) + "\n");
        }
        AirspaceRiskResult air = report.getAirspaceRisk();
        if (air != null) {
            sw.write("# AirspaceRisk," + String.format("%.1f (%s)", air.getOverallRiskScore(), air.getRiskLevel()) + "\n");
        }
        sw.write("# Anomalies," + (report.getAnomalies() != null ? report.getAnomalies().size() : 0) + "\n");
        sw.write("#\n");

        // -- Telemetry table --------------------------------------------------
        CSVFormat fmt = CSVFormat.DEFAULT.builder()
            .setHeader("timestamp_ms", "datetime_utc",
                "latitude", "longitude",
                "altitude_agl_m", "altitude_msl_m",
                "ground_speed_ms", "heading_deg",
                "velocity_north_ms", "velocity_east_ms", "velocity_down_ms",
                "roll_deg", "pitch_deg",
                "battery_pct", "battery_v",
                "flight_mode", "has_gps_fix")
            .build();

        // We need the raw points — extract via summary start/end to rebuild from report
        // Since AnalysisReport doesn't store raw points (by design), we re-ingest here.
        // For now emit anomaly events and summary instead of raw telemetry.
        // In the full product, FlightSession would store points alongside the report.
        try (CSVPrinter printer = new CSVPrinter(sw, fmt)) {
            // Emit anomaly events as special rows
            if (report.getAnomalies() != null) {
                for (AnomalyEvent a : report.getAnomalies()) {
                    printer.printRecord(
                        a.getTimestampMs(),
                        TS_FMT.format(Instant.ofEpochMilli(a.getTimestampMs())),
                        a.getLatitude(), a.getLongitude(),
                        "", "", "", "", "", "", "", "", "",
                        "", "",
                        "ANOMALY:" + a.getType(),
                        ""
                    );
                }
            }
        }

        return sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Serialises a raw telemetry list to CSV with a metadata comment header block.
     * This is the primary CSV export used by the REST endpoint.
     */
    public byte[] telemetryToCsv(List<TelemetryPoint> points, AnalysisReport report) throws IOException {
        StringWriter sw = new StringWriter();

        // ── Metadata block (same format as toCsv) ─────────────────────────────
        sw.write("# Drone Analytics Report\n");
        sw.write("# Generated: " + TS_FMT.format(Instant.now()) + " UTC\n");
        if (report != null) {
            FlightSummary s = report.getSummary();
            if (s != null) {
                sw.write("# DroneType," + report.getDroneType() + "\n");
                sw.write("# Duration(s)," + String.format("%.1f", s.getFlightDurationMs() / 1000.0) + "\n");
                sw.write("# TotalDistance(m)," + String.format("%.1f", s.getTotalDistanceM()) + "\n");
                sw.write("# MaxAGL(m)," + String.format("%.1f", s.getMaxAltitudeAglM()) + "\n");
                sw.write("# BatteryDrain(%)," + String.format("%.1f", s.getBatteryDrainPct()) + "\n");
            }
            PathOptimizationResult p = report.getPathOptimization();
            if (p != null) {
                sw.write("# PathEfficiency(%)," + String.format("%.1f", p.getEfficiencyScore()) + "\n");
            }
            AirspaceRiskResult air = report.getAirspaceRisk();
            if (air != null) {
                sw.write("# AirspaceRisk," + String.format("%.1f (%s)", air.getOverallRiskScore(), air.getRiskLevel()) + "\n");
            }
            sw.write("# Anomalies," + (report.getAnomalies() != null ? report.getAnomalies().size() : 0) + "\n");
        }
        sw.write("#\n");

        CSVFormat fmt = CSVFormat.DEFAULT.builder()
            .setHeader("timestamp_ms", "datetime_utc",
                "latitude", "longitude",
                "altitude_agl_m", "altitude_msl_m",
                "ground_speed_ms", "heading_deg",
                "velocity_north_ms", "velocity_east_ms", "velocity_down_ms",
                "roll_deg", "pitch_deg",
                "battery_pct", "battery_v",
                "flight_mode", "has_gps_fix")
            .build();

        try (CSVPrinter printer = new CSVPrinter(sw, fmt)) {
            for (TelemetryPoint p : points) {
                printer.printRecord(
                    p.getTimestampMs(),
                    TS_FMT.format(Instant.ofEpochMilli(p.getTimestampMs())),
                    round(p.getLatitude(), 7),  round(p.getLongitude(), 7),
                    round(p.getAltitudeAgl(), 2), round(p.getAltitudeMsl(), 2),
                    round(p.getGroundSpeed(), 3), round(p.getHeadingDeg(), 1),
                    round(p.getVelocityNorth(), 3), round(p.getVelocityEast(), 3), round(p.getVelocityDown(), 3),
                    round(p.getRollDeg(), 2), round(p.getPitchDeg(), 2),
                    round(p.getBatteryPercent(), 1), round(p.getBatteryVoltage(), 3),
                    p.getFlightMode(), p.isHasGpsFix()
                );
            }
        }
        return sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    /**
     * Generates a one-page PDF flight report using Apache PDFBox.
     */
    public byte[] toPdf(AnalysisReport report) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDType1Font bold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font mono    = new PDType1Font(Standard14Fonts.FontName.COURIER);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float margin = 50;
                float y = page.getMediaBox().getHeight() - margin;
                float lineH = 18;
                float pageW = page.getMediaBox().getWidth() - 2 * margin;

                // ── Title ────────────────────────────────────────────────────
                y = drawText(cs, bold, 18, margin, y, "Drone Flight Analytics Report");
                y -= 6;
                y = drawText(cs, regular, 10, margin, y,
                    "Generated: " + TS_FMT.format(Instant.now()) + " UTC  |  " +
                    "Drone type: " + report.getDroneType());
                y = drawLine(cs, margin, y - 4, margin + pageW);

                // ── Flight Summary ────────────────────────────────────────────
                FlightSummary s = report.getSummary();
                y = sectionHeader(cs, bold, margin, y - lineH, "1. Flight Summary");
                if (s != null) {
                    y = kv(cs, regular, mono, margin, y, "Duration",
                        String.format("%.0f s (%.1f min)", s.getFlightDurationMs()/1000.0, s.getFlightDurationMs()/60000.0));
                    y = kv(cs, regular, mono, margin, y, "Total distance",
                        String.format("%.0f m", s.getTotalDistanceM()));
                    y = kv(cs, regular, mono, margin, y, "Max altitude AGL",
                        String.format("%.1f m", s.getMaxAltitudeAglM()));
                    y = kv(cs, regular, mono, margin, y, "Avg / max speed",
                        String.format("%.1f / %.1f m/s", s.getAvgGroundSpeedMs(), s.getMaxGroundSpeedMs()));
                    y = kv(cs, regular, mono, margin, y, "Battery drain",
                        String.format("%.1f %%", s.getBatteryDrainPct()));
                    y = kv(cs, regular, mono, margin, y, "Sample points",
                        String.format("%d @ %.1f Hz", s.getTotalPoints(), s.getSamplingRateHz()));
                }

                // ── Path Optimisation ─────────────────────────────────────────
                PathOptimizationResult p = report.getPathOptimization();
                y = sectionHeader(cs, bold, margin, y - lineH, "2. Path Optimisation");
                if (p != null) {
                    y = kv(cs, regular, mono, margin, y, "Great-circle distance",
                        String.format("%.0f m", p.getGreatCircleDistanceM()));
                    y = kv(cs, regular, mono, margin, y, "Actual distance",
                        String.format("%.0f m", p.getActualDistanceM()));
                    y = kv(cs, regular, mono, margin, y, "Efficiency score",
                        String.format("%.1f %% (%s)", p.getEfficiencyScore(), p.getEfficiencyRating()));
                    y = kv(cs, regular, mono, margin, y, "Wasted distance",
                        String.format("%.0f m", p.getWastedDistanceM()));
                    y = kv(cs, regular, mono, margin, y, "Mean bearing deviation",
                        String.format("%.1f deg", p.getMeanBearingDeviationDeg()));
                }

                // ── Battery Model ─────────────────────────────────────────────
                BatteryModel b = report.getBatteryModel();
                y = sectionHeader(cs, bold, margin, y - lineH, "3. Battery Discharge Model");
                if (b != null && b.getRSquared() > 0) {
                    y = kv(cs, regular, mono, margin, y, "Model fit (R²)",
                        String.format("%.4f", b.getRSquared()));
                    y = kv(cs, regular, mono, margin, y, "Total drain",
                        String.format("%.1f %% (%.1f %% -> %.1f %%)",
                            b.getTotalDrainPct(), b.getInitialBatteryPct(), b.getFinalBatteryPct()));
                    y = kv(cs, regular, mono, margin, y, "Avg discharge rate",
                        String.format("%.2f %%/min", b.getAvgDischargeRatePerMin()));
                    double lowT = b.getPredictedLowBatteryTimeSec();
                    y = kv(cs, regular, mono, margin, y, "Predicted 20% battery",
                        Double.isNaN(lowT) || lowT < 0 ? "Not reached in flight" :
                            String.format("%.0f s from takeoff", lowT));
                }

                // ── Airspace Risk ─────────────────────────────────────────────
                AirspaceRiskResult air = report.getAirspaceRisk();
                y = sectionHeader(cs, bold, margin, y - lineH, "4. Airspace Risk");
                if (air != null) {
                    y = kv(cs, regular, mono, margin, y, "Overall risk score",
                        String.format("%.1f / 100 (%s)", air.getOverallRiskScore(), air.getRiskLevel()));
                    y = kv(cs, regular, mono, margin, y, "Nearest restriction",
                        air.getNearestZoneName() + (Double.isNaN(air.getMinDistanceToRestrictionM()) ? "" :
                            String.format(" (%.0f m away)", air.getMinDistanceToRestrictionM())));
                    y = kv(cs, regular, mono, margin, y, "Inside restricted zone",
                        air.isInsideRestrictedZone() ? "YES — VIOLATION" : "No");
                }

                // ── Anomalies ─────────────────────────────────────────────────
                List<AnomalyEvent> anomalies = report.getAnomalies();
                y = sectionHeader(cs, bold, margin, y - lineH, "5. Anomalies Detected");
                if (anomalies == null || anomalies.isEmpty()) {
                    y = drawText(cs, regular, 11, margin + 10, y - lineH, "None — flight data is clean.");
                } else {
                    for (AnomalyEvent a : anomalies) {
                        String line = String.format("[%s] %s — %s",
                            a.getSeverity(), a.getType(), a.getDescription());
                        y = drawText(cs, regular, 10, margin + 10, y - lineH, line);
                        if (y < 80) break; // prevent overflow off page
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    // ── PDF layout helpers ────────────────────────────────────────────────────

    private float drawText(PDPageContentStream cs, PDType1Font font,
                           float size, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text != null ? text : "");
        cs.endText();
        return y - size - 4;
    }

    private float drawLine(PDPageContentStream cs, float x1, float y, float x2) throws IOException {
        cs.moveTo(x1, y); cs.lineTo(x2, y); cs.stroke();
        return y - 4;
    }

    private float sectionHeader(PDPageContentStream cs, PDType1Font bold,
                                 float x, float y, String title) throws IOException {
        return drawText(cs, bold, 13, x, y, title);
    }

    private float kv(PDPageContentStream cs, PDType1Font label, PDType1Font value,
                      float x, float y, String key, String val) throws IOException {
        float nextY = drawText(cs, label, 11, x + 10, y - 16, key + ":");
        drawText(cs, value, 11, x + 160, y - 16, val);
        return nextY;
    }

    private double round(double v, int dp) {
        double scale = Math.pow(10, dp);
        return Math.round(v * scale) / scale;
    }
}
