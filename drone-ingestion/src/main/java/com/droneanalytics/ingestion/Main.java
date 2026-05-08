package com.droneanalytics.ingestion;

import com.droneanalytics.analytics.engine.AnalyticsEngine;
import com.droneanalytics.analytics.model.*;
import com.droneanalytics.ingestion.model.DroneType;
import com.droneanalytics.ingestion.model.TelemetryPoint;

import java.nio.file.Path;
import java.util.DoubleSummaryStatistics;
import java.util.List;

/**
 * Demo entry point — Step 1 ingestion summary + Step 2 analytics report.
 *
 * Run: java -jar target/drone-ingestion-fat.jar
 * Or:  mvn exec:java -Dexec.mainClass=com.droneanalytics.ingestion.Main
 */
public class Main {

    public static void main(String[] args) {
        FlightLogIngestionService ingestionSvc = new FlightLogIngestionService();
        AnalyticsEngine           analyticsSvc = new AnalyticsEngine();

        String[][] samples = {
            {"src/test/resources/sample_dji.txt",        "DJI TXT"},
            {"src/test/resources/sample_ardupilot.bin",  "ArduPilot BIN"},
            {"src/test/resources/sample_px4.ulg",        "PX4 ULog"},
            {"src/test/resources/sample_mavlink.tlog",   "MAVLink TLOG"},
            {"src/test/resources/sample_parrot.json",    "Parrot JSON"},
        };

        // ── Step 1: Ingestion summary ─────────────────────────────────────────
        printIngestionHeader();
        for (String[] entry : samples) {
            try {
                List<TelemetryPoint> points = ingestionSvc.ingest(Path.of(entry[0]));
                printIngestionRow(entry[1], points);
            } catch (Exception e) {
                System.out.printf("  %-18s  ERROR: %s%n", entry[1], e.getMessage());
            }
        }

        // ── Step 2: Analytics report ──────────────────────────────────────────
        System.out.println();
        printAnalyticsHeader();
        for (String[] entry : samples) {
            try {
                AnalysisReport report = analyticsSvc.analyze(Path.of(entry[0]));
                printAnalyticsRow(entry[1], report);
            } catch (Exception e) {
                System.out.printf("  %-18s  ERROR: %s%n", entry[1], e.getMessage());
            }
        }
    }

    // ── Ingestion table ───────────────────────────────────────────────────────

    private static void printIngestionHeader() {
        System.out.println("=".repeat(90));
        System.out.println("  STEP 1 — Ingestion: Format Normalisation");
        System.out.println("=".repeat(90));
        System.out.printf("  %-18s %-8s %-10s %-14s %-14s %-10s%n",
            "Format", "Points", "DroneType", "Alt AGL (m)", "Speed (m/s)", "Bat %");
        System.out.println("-".repeat(90));
    }

    private static void printIngestionRow(String label, List<TelemetryPoint> points) {
        if (points.isEmpty()) { System.out.printf("  %-18s  (no points)%n", label); return; }
        DroneType type = points.get(0).getDroneType();
        DoubleSummaryStatistics agl = points.stream().mapToDouble(TelemetryPoint::getAltitudeAgl).summaryStatistics();
        DoubleSummaryStatistics spd = points.stream().mapToDouble(TelemetryPoint::getGroundSpeed).summaryStatistics();
        DoubleSummaryStatistics bat = points.stream().mapToDouble(TelemetryPoint::getBatteryPercent).summaryStatistics();
        System.out.printf("  %-18s %-8d %-10s %-14s %-14s %-10s%n",
            label, points.size(), type,
            String.format("%.1f-%.1f", agl.getMin(), agl.getMax()),
            String.format("%.1f avg",  spd.getAverage()),
            String.format("%.0f-%.0f", bat.getMin(), bat.getMax()));
    }

    // ── Analytics table ───────────────────────────────────────────────────────

    private static void printAnalyticsHeader() {
        System.out.println("=".repeat(100));
        System.out.println("  STEP 2 — Analytics: Flight Report");
        System.out.println("=".repeat(100));
        System.out.printf("  %-18s %-10s %-14s %-16s %-12s %-14s %-14s%n",
            "Format", "Duration", "Distance(m)", "Path Eff.(%)", "Anomalies",
            "Bat drain(%)", "Airspace Risk");
        System.out.println("-".repeat(100));
    }

    private static void printAnalyticsRow(String label, AnalysisReport r) {
        FlightSummary          s   = r.getSummary();
        PathOptimizationResult p   = r.getPathOptimization();
        BatteryModel           b   = r.getBatteryModel();
        AirspaceRiskResult     air = r.getAirspaceRisk();

        System.out.printf("  %-18s %-10s %-14s %-16s %-12s %-14s %-14s%n",
            label,
            String.format("%.0fs",   s.getFlightDurationMs() / 1000.0),
            String.format("%.0f",    s.getTotalDistanceM()),
            String.format("%.1f (%s)", p.getEfficiencyScore(), p.getEfficiencyRating()),
            r.getAnomalies().size() + " events",
            String.format("%.1f%%",  b.getTotalDrainPct()),
            String.format("%.1f (%s)", air.getOverallRiskScore(), air.getRiskLevel())
        );

        // Print battery model detail
        if (b.getRSquared() > 0) {
            System.out.printf("    Battery: R²=%.3f, rate=%.2f%%/min, low@%.0fs, crit@%.0fs%n",
                b.getRSquared(), b.getAvgDischargeRatePerMin(),
                Double.isNaN(b.getPredictedLowBatteryTimeSec())   ? -1 : b.getPredictedLowBatteryTimeSec(),
                Double.isNaN(b.getPredictedCriticalBatteryTimeSec()) ? -1 : b.getPredictedCriticalBatteryTimeSec());
        }

        // Print anomalies (if any)
        for (AnomalyEvent a : r.getAnomalies()) {
            System.out.printf("    [%s] %s%n", a.getSeverity(), a.getDescription());
        }

        // Print nearest airspace zone
        if (!air.getProximityEvents().isEmpty()) {
            AirspaceRiskResult.ZoneProximityEvent nearest = air.getProximityEvents().get(0);
            System.out.printf("    Airspace: nearest=%s (%.0fm away, risk=%.1f)%n",
                nearest.getZoneName(), nearest.getDistanceM(), nearest.getRiskContribution());
        }
    }
}
