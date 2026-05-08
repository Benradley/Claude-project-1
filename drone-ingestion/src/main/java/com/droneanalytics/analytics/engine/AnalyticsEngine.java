package com.droneanalytics.analytics.engine;

import com.droneanalytics.analytics.model.*;
import com.droneanalytics.ingestion.FlightLogIngestionService;
import com.droneanalytics.ingestion.model.TelemetryPoint;
import com.droneanalytics.ingestion.parser.ParseException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Main entry point for the analytics pipeline (FR-3).
 *
 * Chains all five analytics engines in sequence and returns a single
 * {@link AnalysisReport} containing every result.
 *
 * Usage — from raw telemetry:
 * <pre>
 *   AnalyticsEngine engine = new AnalyticsEngine();
 *   AnalysisReport report  = engine.analyze(telemetryPoints);
 * </pre>
 *
 * Usage — directly from a log file:
 * <pre>
 *   AnalysisReport report = new AnalyticsEngine().analyze(Path.of("flight.bin"));
 * </pre>
 */
@Service
public class AnalyticsEngine {

    private final StatisticsCalculator    statsCalc   = new StatisticsCalculator();
    private final PathOptimizer           pathOpt     = new PathOptimizer();
    private final BatteryRegressionEngine batteryEng  = new BatteryRegressionEngine();
    private final AnomalyDetector         anomalyDet  = new AnomalyDetector();
    private final AirspaceRiskScorer      riskScorer  = new AirspaceRiskScorer();
    private final FlightLogIngestionService ingestion = new FlightLogIngestionService();

    // -------------------------------------------------------------------------
    // Primary API
    // -------------------------------------------------------------------------

    /**
     * Runs the full analytics pipeline over a list of normalised telemetry points.
     *
     * @param points Chronologically ordered TelemetryPoints from the ingestion pipeline.
     * @return Populated AnalysisReport. Never null.
     * @throws IllegalArgumentException if points is null or has fewer than 2 entries.
     */
    public AnalysisReport analyze(List<TelemetryPoint> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException(
                "Analytics requires at least 2 telemetry points; got: " +
                (points == null ? "null" : points.size()));
        }

        // 1. Flight statistics (must run first — totalDistance feeds battery engine)
        FlightSummary summary = statsCalc.calculate(points);

        // 2. Path optimisation
        PathOptimizationResult pathResult = pathOpt.analyze(points);

        // 3. Battery discharge model
        BatteryModel batteryModel = batteryEng.fit(points, summary.getTotalDistanceM());

        // 4. Anomaly detection
        List<AnomalyEvent> anomalies = anomalyDet.detect(points);

        // 5. Airspace risk scoring
        AirspaceRiskResult riskResult = riskScorer.score(points);

        // Assemble report
        AnalysisReport report = new AnalysisReport();
        report.setDroneType(points.get(0).getDroneType());
        report.setSummary(summary);
        report.setPathOptimization(pathResult);
        report.setBatteryModel(batteryModel);
        report.setAnomalies(anomalies);
        report.setAirspaceRisk(riskResult);
        return report;
    }

    /**
     * Convenience overload: ingests a log file then runs analytics.
     *
     * @param logFile Path to any supported drone log file
     *                (.txt, .bin, .ulg, .tlog, .json).
     * @return Populated AnalysisReport.
     * @throws IOException    if the file cannot be read.
     * @throws ParseException if the format is unrecognised or malformed.
     * @throws IllegalArgumentException if the file yields fewer than 2 points.
     */
    public AnalysisReport analyze(Path logFile) throws IOException, ParseException {
        List<TelemetryPoint> points = ingestion.ingest(logFile);
        return analyze(points);
    }
}
