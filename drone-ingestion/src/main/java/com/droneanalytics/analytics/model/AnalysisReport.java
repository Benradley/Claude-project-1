package com.droneanalytics.analytics.model;

import com.droneanalytics.ingestion.model.DroneType;
import java.util.List;

/**
 * Top-level output of the analytics engine for a single flight.
 * Aggregates all sub-analysis results into one report object.
 */
public class AnalysisReport {

    private DroneType           droneType;
    private FlightSummary       summary;
    private PathOptimizationResult pathOptimization;
    private BatteryModel        batteryModel;
    private List<AnomalyEvent>  anomalies;
    private AirspaceRiskResult  airspaceRisk;

    /** True if the flight had no anomalies and low airspace risk. */
    public boolean isFlightHealthy() {
        boolean noAnomalies = anomalies == null || anomalies.isEmpty();
        boolean lowRisk = airspaceRisk == null
            || airspaceRisk.getOverallRiskScore() < 26;
        return noAnomalies && lowRisk;
    }

    /** Returns a short one-line status suitable for a dashboard tile. */
    public String getStatusLine() {
        int anomalyCount = anomalies != null ? anomalies.size() : 0;
        String riskLevel  = airspaceRisk != null ? airspaceRisk.getRiskLevel() : "Unknown";
        double efficiency = pathOptimization != null ? pathOptimization.getEfficiencyScore() : 0;
        return String.format("Anomalies: %d | Risk: %s | Path efficiency: %.1f%%",
            anomalyCount, riskLevel, efficiency);
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public DroneType getDroneType() { return droneType; }
    public void setDroneType(DroneType v) { this.droneType = v; }

    public FlightSummary getSummary() { return summary; }
    public void setSummary(FlightSummary v) { this.summary = v; }

    public PathOptimizationResult getPathOptimization() { return pathOptimization; }
    public void setPathOptimization(PathOptimizationResult v) { this.pathOptimization = v; }

    public BatteryModel getBatteryModel() { return batteryModel; }
    public void setBatteryModel(BatteryModel v) { this.batteryModel = v; }

    public List<AnomalyEvent> getAnomalies() { return anomalies; }
    public void setAnomalies(List<AnomalyEvent> v) { this.anomalies = v; }

    public AirspaceRiskResult getAirspaceRisk() { return airspaceRisk; }
    public void setAirspaceRisk(AirspaceRiskResult v) { this.airspaceRisk = v; }

    @Override
    public String toString() {
        return String.format("AnalysisReport{%s | %s | %s | anomalies=%d | %s}",
            droneType, summary, pathOptimization,
            anomalies != null ? anomalies.size() : 0, airspaceRisk);
    }
}
