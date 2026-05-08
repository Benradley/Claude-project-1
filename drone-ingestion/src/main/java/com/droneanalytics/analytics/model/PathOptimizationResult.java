package com.droneanalytics.analytics.model;

import java.util.List;

/**
 * Compares the actual flown path against the theoretical optimal (great-circle) path.
 *
 * Efficiency score: how closely the drone followed the straight-line ideal.
 *   100 = perfectly straight line from A to B
 *     0 = wildly inefficient
 */
public class PathOptimizationResult {

    /** Great-circle (straight-line) distance start→end in metres. */
    private double greatCircleDistanceM;

    /** Actual accumulated ground-track distance in metres. */
    private double actualDistanceM;

    /**
     * Path efficiency score 0–100.
     * = (greatCircleDistance / actualDistance) × 100
     * Capped at 100 — scores above 100 are impossible (actual < great-circle only for teleportation).
     */
    private double efficiencyScore;

    /**
     * Extra distance flown compared to the optimal straight line (m).
     * = actualDistance - greatCircleDistance
     */
    private double wastedDistanceM;

    /**
     * Per-segment deviation: at each telemetry point, the bearing deviation (degrees)
     * between the actual heading and the heading toward the final destination.
     */
    private List<Double> bearingDeviationsDeg;

    /** Mean absolute bearing deviation across all points (degrees). */
    private double meanBearingDeviationDeg;

    /** Maximum bearing deviation recorded during the flight (degrees). */
    private double maxBearingDeviationDeg;

    /**
     * Human-readable efficiency rating band.
     * Excellent ≥90 | Good 75–89 | Fair 50–74 | Poor <50
     */
    public String getEfficiencyRating() {
        if (efficiencyScore >= 90) return "Excellent";
        if (efficiencyScore >= 75) return "Good";
        if (efficiencyScore >= 50) return "Fair";
        return "Poor";
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public double getGreatCircleDistanceM() { return greatCircleDistanceM; }
    public void setGreatCircleDistanceM(double v) { this.greatCircleDistanceM = v; }

    public double getActualDistanceM() { return actualDistanceM; }
    public void setActualDistanceM(double v) { this.actualDistanceM = v; }

    public double getEfficiencyScore() { return efficiencyScore; }
    public void setEfficiencyScore(double v) { this.efficiencyScore = v; }

    public double getWastedDistanceM() { return wastedDistanceM; }
    public void setWastedDistanceM(double v) { this.wastedDistanceM = v; }

    public List<Double> getBearingDeviationsDeg() { return bearingDeviationsDeg; }
    public void setBearingDeviationsDeg(List<Double> v) { this.bearingDeviationsDeg = v; }

    public double getMeanBearingDeviationDeg() { return meanBearingDeviationDeg; }
    public void setMeanBearingDeviationDeg(double v) { this.meanBearingDeviationDeg = v; }

    public double getMaxBearingDeviationDeg() { return maxBearingDeviationDeg; }
    public void setMaxBearingDeviationDeg(double v) { this.maxBearingDeviationDeg = v; }

    @Override
    public String toString() {
        return String.format(
            "PathOptimization{gc=%.0fm, actual=%.0fm, efficiency=%.1f%% (%s), wasted=%.0fm, meanDev=%.1fdeg}",
            greatCircleDistanceM, actualDistanceM, efficiencyScore,
            getEfficiencyRating(), wastedDistanceM, meanBearingDeviationDeg
        );
    }
}
