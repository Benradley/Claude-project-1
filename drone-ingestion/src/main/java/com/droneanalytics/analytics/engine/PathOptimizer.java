package com.droneanalytics.analytics.engine;

import com.droneanalytics.analytics.math.HaversineCalculator;
import com.droneanalytics.analytics.model.PathOptimizationResult;
import com.droneanalytics.ingestion.model.TelemetryPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Scores path efficiency by comparing the actual flown route against the
 * theoretical great-circle (shortest) path from start to finish.
 *
 * Two metrics are computed:
 *
 * 1. Distance efficiency:
 *    How much extra distance was flown compared to the straight line?
 *    Score = (greatCircle / actualDistance) × 100, capped at 100.
 *    A circular orbit deliberately scores low — this is correct and expected.
 *
 * 2. Bearing deviation:
 *    At each point, the heading toward the destination vs the actual heading.
 *    High deviation = drone went sideways or backtracked.
 *    (Note: hover / circular missions will inherently have high deviation.)
 */
public class PathOptimizer {

    /**
     * Analyses the flight path and returns a PathOptimizationResult.
     *
     * @param points Chronologically ordered TelemetryPoints (minimum 2).
     * @return PathOptimizationResult with efficiency scores and bearing deviations.
     */
    public PathOptimizationResult analyze(List<TelemetryPoint> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 points for path analysis");
        }

        TelemetryPoint start = points.get(0);
        TelemetryPoint end   = points.get(points.size() - 1);

        // --- Great-circle distance start → end ---
        double gcDist = HaversineCalculator.distanceM(
            start.getLatitude(), start.getLongitude(),
            end.getLatitude(),   end.getLongitude()
        );

        // --- Accumulated actual distance ---
        double actualDist = 0;
        for (int i = 1; i < points.size(); i++) {
            TelemetryPoint prev = points.get(i - 1);
            TelemetryPoint curr = points.get(i);
            actualDist += HaversineCalculator.distanceM(
                prev.getLatitude(), prev.getLongitude(),
                curr.getLatitude(), curr.getLongitude()
            );
        }

        // --- Path efficiency score ---
        double efficiencyScore;
        if (actualDist < 1.0) {
            // Drone barely moved — hovering in place, call it 100%
            efficiencyScore = 100.0;
        } else {
            efficiencyScore = Math.min(100.0, (gcDist / actualDist) * 100.0);
        }

        // --- Per-point bearing deviation from optimal heading (toward destination) ---
        List<Double> bearingDeviations = new ArrayList<>();
        double sumDeviation = 0;
        double maxDeviation = 0;

        for (int i = 0; i < points.size() - 1; i++) {
            TelemetryPoint curr = points.get(i);

            // Optimal bearing: current position → final destination
            double optimalBearing = HaversineCalculator.bearingDeg(
                curr.getLatitude(), curr.getLongitude(),
                end.getLatitude(),  end.getLongitude()
            );

            // Actual heading from telemetry
            double actualHeading = curr.getHeadingDeg();

            // Skip points with zero heading (hovering / no data)
            if (actualHeading == 0 && curr.getGroundSpeed() < 0.5) {
                bearingDeviations.add(0.0);
                continue;
            }

            double deviation = HaversineCalculator.headingDifferenceDeg(actualHeading, optimalBearing);
            bearingDeviations.add(deviation);
            sumDeviation += deviation;
            if (deviation > maxDeviation) maxDeviation = deviation;
        }

        double meanDeviation = bearingDeviations.isEmpty()
            ? 0 : sumDeviation / bearingDeviations.size();

        // --- Assemble result ---
        PathOptimizationResult result = new PathOptimizationResult();
        result.setGreatCircleDistanceM(gcDist);
        result.setActualDistanceM(actualDist);
        result.setEfficiencyScore(efficiencyScore);
        result.setWastedDistanceM(Math.max(0, actualDist - gcDist));
        result.setBearingDeviationsDeg(bearingDeviations);
        result.setMeanBearingDeviationDeg(meanDeviation);
        result.setMaxBearingDeviationDeg(maxDeviation);
        return result;
    }
}
