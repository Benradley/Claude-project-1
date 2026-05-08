package com.droneanalytics.analytics.engine;

import com.droneanalytics.analytics.math.PolynomialRegression;
import com.droneanalytics.analytics.model.BatteryModel;
import com.droneanalytics.ingestion.model.TelemetryPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Models battery discharge using polynomial regression over flight telemetry.
 *
 * Fits:  battPct(t) = a·t² + b·t + c
 * where t is elapsed time in seconds from flight start.
 *
 * From the fitted curve we predict:
 *   - When the battery will reach 20% (low warning threshold)
 *   - When the battery will reach 10% (critical/RTL threshold)
 *   - Battery efficiency in % consumed per kilometre flown
 *
 * Design choice — quadratic (degree 2):
 *   Real LiPo discharge is slightly non-linear: faster drain under high load,
 *   with a knee curve near end of life. A quadratic captures this behaviour
 *   without overfitting. Linear (degree 1) is used as fallback when data is sparse.
 */
public class BatteryRegressionEngine {

    private static final double LOW_BATTERY_THRESHOLD      = 20.0; // % warning
    private static final double CRITICAL_BATTERY_THRESHOLD = 10.0; // % RTL trigger
    private static final int    MIN_POINTS_FOR_QUADRATIC   = 5;

    /**
     * Fits a discharge model to the battery data in the telemetry stream.
     *
     * @param points Chronologically ordered TelemetryPoints.
     * @param totalDistanceM Total ground-track distance (from StatisticsCalculator).
     */
    public BatteryModel fit(List<TelemetryPoint> points, double totalDistanceM) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("Cannot fit battery model to empty point list");
        }

        // --- Extract (t, battPct) pairs where battery data exists ---
        long t0 = points.get(0).getTimestampMs();
        List<double[]> samples = new ArrayList<>();

        for (TelemetryPoint p : points) {
            if (p.getBatteryPercent() > 0) {
                double tSec = (p.getTimestampMs() - t0) / 1000.0;
                samples.add(new double[]{tSec, p.getBatteryPercent()});
            }
        }

        if (samples.size() < 2) {
            // Not enough battery data — return a minimal model
            return buildEmptyModel();
        }

        double[] tArr    = samples.stream().mapToDouble(s -> s[0]).toArray();
        double[] battArr = samples.stream().mapToDouble(s -> s[1]).toArray();

        // --- Choose degree: quadratic if enough data, else linear ---
        int degree = (samples.size() >= MIN_POINTS_FOR_QUADRATIC) ? 2 : 1;
        PolynomialRegression regression = new PolynomialRegression(tArr, battArr, degree);

        // --- Build the model ---
        BatteryModel model = new BatteryModel();
        model.setCoefficients(regression.getCoefficients());
        model.setRSquared(regression.getRSquared());
        model.setInitialBatteryPct(battArr[0]);
        model.setFinalBatteryPct(battArr[battArr.length - 1]);
        model.setTotalDrainPct(Math.max(0, battArr[0] - battArr[battArr.length - 1]));

        double flightDurationSec = tArr[tArr.length - 1];

        // --- Average discharge rate (% per minute) ---
        if (flightDurationSec > 0) {
            model.setAvgDischargeRatePerMin(
                model.getTotalDrainPct() / (flightDurationSec / 60.0)
            );
        }

        // --- Predict times for threshold crossings ---
        model.setPredictedLowBatteryTimeSec(
            predictThresholdCrossing(regression, flightDurationSec, LOW_BATTERY_THRESHOLD)
        );
        model.setPredictedCriticalBatteryTimeSec(
            predictThresholdCrossing(regression, flightDurationSec, CRITICAL_BATTERY_THRESHOLD)
        );

        // --- Remaining time at end of flight (until 20%) ---
        double finalPct = regression.evaluate(flightDurationSec);
        double ratePerSec = model.getAvgDischargeRatePerMin() / 60.0;
        if (ratePerSec > 0 && finalPct > LOW_BATTERY_THRESHOLD) {
            model.setEstimatedRemainingTimeSec((finalPct - LOW_BATTERY_THRESHOLD) / ratePerSec);
        }

        // --- Battery efficiency: % per km ---
        if (totalDistanceM > 10) {
            model.setBatteryPercentPerKm(model.getTotalDrainPct() / (totalDistanceM / 1000.0));
        }

        return model;
    }

    /**
     * Binary-search the polynomial for when it crosses a threshold percentage.
     * Searches from the last known time forward (up to 3× flight duration).
     *
     * @return Elapsed seconds at which the threshold is crossed, or Double.NaN if not found.
     */
    private double predictThresholdCrossing(PolynomialRegression reg,
                                             double flightDurationSec,
                                             double targetPct) {
        // Start searching from flight duration onward (future prediction)
        double tMax  = flightDurationSec * 3;
        double tLow  = 0;
        double tHigh = tMax;

        // Check that the polynomial does eventually cross the threshold
        if (reg.evaluate(tHigh) > targetPct) return Double.NaN;

        // Binary search for the crossing (100 iterations → ~1e-30 precision)
        for (int i = 0; i < 100; i++) {
            double tMid = (tLow + tHigh) / 2;
            if (reg.evaluate(tMid) > targetPct) {
                tLow = tMid;
            } else {
                tHigh = tMid;
            }
            if (tHigh - tLow < 0.01) break; // converged to 10ms precision
        }
        return (tLow + tHigh) / 2;
    }

    private BatteryModel buildEmptyModel() {
        BatteryModel m = new BatteryModel();
        m.setCoefficients(new double[]{0, 0, 0});
        m.setRSquared(0);
        return m;
    }
}
