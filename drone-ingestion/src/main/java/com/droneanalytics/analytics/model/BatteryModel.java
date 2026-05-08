package com.droneanalytics.analytics.model;

/**
 * Battery discharge model fitted from flight telemetry using polynomial regression.
 *
 * Models battery % as a function of elapsed flight time:
 *   battPct(t) = a·t² + b·t + c
 *
 * From this model we derive:
 *   - Discharge rate (% per minute)
 *   - Predicted time to low-battery warning (20%)
 *   - Predicted time to critical battery (10%)
 *   - Efficiency: battery % consumed per kilometre flown
 */
public class BatteryModel {

    /** Polynomial coefficients [a, b, c] where battPct(t) = a·t² + b·t + c, t in seconds. */
    private double[] coefficients;

    /** R² goodness-of-fit (0–1, higher is better). */
    private double rSquared;

    /** Average discharge rate over the full flight (% per minute). */
    private double avgDischargeRatePerMin;

    /** Estimated remaining flight time at end of flight (seconds) until 20% battery. */
    private double estimatedRemainingTimeSec;

    /** Predicted elapsed time when battery reaches 20% (seconds from flight start). */
    private double predictedLowBatteryTimeSec;

    /** Predicted elapsed time when battery reaches 10% (seconds from flight start). */
    private double predictedCriticalBatteryTimeSec;

    /** Battery % consumed per kilometre flown (efficiency metric). */
    private double batteryPercentPerKm;

    /** Starting battery % observed in the flight. */
    private double initialBatteryPct;

    /** Ending battery % observed in the flight. */
    private double finalBatteryPct;

    /** Total battery % drained. */
    private double totalDrainPct;

    /**
     * Evaluates the polynomial model at time t (seconds from flight start).
     * Returns predicted battery percentage.
     */
    public double predict(double tSeconds) {
        if (coefficients == null || coefficients.length < 3) return Double.NaN;
        return coefficients[0] * tSeconds * tSeconds
             + coefficients[1] * tSeconds
             + coefficients[2];
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public double[] getCoefficients() { return coefficients; }
    public void setCoefficients(double[] v) { this.coefficients = v; }

    public double getRSquared() { return rSquared; }
    public void setRSquared(double v) { this.rSquared = v; }

    public double getAvgDischargeRatePerMin() { return avgDischargeRatePerMin; }
    public void setAvgDischargeRatePerMin(double v) { this.avgDischargeRatePerMin = v; }

    public double getEstimatedRemainingTimeSec() { return estimatedRemainingTimeSec; }
    public void setEstimatedRemainingTimeSec(double v) { this.estimatedRemainingTimeSec = v; }

    public double getPredictedLowBatteryTimeSec() { return predictedLowBatteryTimeSec; }
    public void setPredictedLowBatteryTimeSec(double v) { this.predictedLowBatteryTimeSec = v; }

    public double getPredictedCriticalBatteryTimeSec() { return predictedCriticalBatteryTimeSec; }
    public void setPredictedCriticalBatteryTimeSec(double v) { this.predictedCriticalBatteryTimeSec = v; }

    public double getBatteryPercentPerKm() { return batteryPercentPerKm; }
    public void setBatteryPercentPerKm(double v) { this.batteryPercentPerKm = v; }

    public double getInitialBatteryPct() { return initialBatteryPct; }
    public void setInitialBatteryPct(double v) { this.initialBatteryPct = v; }

    public double getFinalBatteryPct() { return finalBatteryPct; }
    public void setFinalBatteryPct(double v) { this.finalBatteryPct = v; }

    public double getTotalDrainPct() { return totalDrainPct; }
    public void setTotalDrainPct(double v) { this.totalDrainPct = v; }

    @Override
    public String toString() {
        return String.format(
            "BatteryModel{R²=%.3f, drain=%.1f%%, rate=%.2f%%/min, " +
            "low@%.0fs, critical@%.0fs, %.2f%%/km}",
            rSquared, totalDrainPct, avgDischargeRatePerMin,
            predictedLowBatteryTimeSec, predictedCriticalBatteryTimeSec,
            batteryPercentPerKm
        );
    }
}
