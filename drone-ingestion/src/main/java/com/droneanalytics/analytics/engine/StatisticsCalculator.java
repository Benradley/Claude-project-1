package com.droneanalytics.analytics.engine;

import com.droneanalytics.analytics.math.HaversineCalculator;
import com.droneanalytics.analytics.model.FlightSummary;
import com.droneanalytics.ingestion.model.TelemetryPoint;

import java.util.List;

/**
 * Computes aggregate flight statistics from a list of normalised TelemetryPoints.
 *
 * Calculates: duration, total distance, max/avg speed, max altitude,
 * battery drain, sampling rate, and straight-line start→end distance.
 */
public class StatisticsCalculator {

    /**
     * Computes a FlightSummary from the given telemetry.
     *
     * @param points Chronologically ordered TelemetryPoints (at least 2).
     * @return FlightSummary populated with all aggregate statistics.
     */
    public FlightSummary calculate(List<TelemetryPoint> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 telemetry points for statistics");
        }

        FlightSummary summary = new FlightSummary();

        TelemetryPoint first = points.get(0);
        TelemetryPoint last  = points.get(points.size() - 1);

        // --- Timestamps ---
        summary.setStartTimestampMs(first.getTimestampMs());
        summary.setEndTimestampMs(last.getTimestampMs());
        summary.setFlightDurationMs(last.getTimestampMs() - first.getTimestampMs());

        // --- Start / end positions ---
        summary.setStartLatitude(first.getLatitude());
        summary.setStartLongitude(first.getLongitude());
        summary.setEndLatitude(last.getLatitude());
        summary.setEndLongitude(last.getLongitude());

        // --- Straight-line (great-circle) start→end ---
        summary.setStraightLineDistanceM(
            HaversineCalculator.distanceM(
                first.getLatitude(), first.getLongitude(),
                last.getLatitude(),  last.getLongitude()
            )
        );

        // --- Accumulated ground-track distance ---
        double totalDist = 0;
        for (int i = 1; i < points.size(); i++) {
            TelemetryPoint prev = points.get(i - 1);
            TelemetryPoint curr = points.get(i);
            totalDist += HaversineCalculator.distanceM(
                prev.getLatitude(), prev.getLongitude(),
                curr.getLatitude(), curr.getLongitude()
            );
        }
        summary.setTotalDistanceM(totalDist);

        // --- Speed ---
        double maxSpeed = 0, sumSpeed = 0;
        for (TelemetryPoint p : points) {
            sumSpeed += p.getGroundSpeed();
            if (p.getGroundSpeed() > maxSpeed) maxSpeed = p.getGroundSpeed();
        }
        summary.setAvgGroundSpeedMs(sumSpeed / points.size());
        summary.setMaxGroundSpeedMs(maxSpeed);

        // --- Altitude ---
        double maxAgl = 0;
        for (TelemetryPoint p : points) {
            if (p.getAltitudeAgl() > maxAgl) maxAgl = p.getAltitudeAgl();
        }
        summary.setMaxAltitudeAglM(maxAgl);

        // --- Battery drain (start% - end%) ---
        double startBatt = firstNonZero(points, true);
        double endBatt   = firstNonZero(points, false);
        summary.setBatteryDrainPct(Math.max(0, startBatt - endBatt));

        // --- Point count & sampling rate ---
        summary.setTotalPoints(points.size());
        double durationSec = summary.getFlightDurationMs() / 1000.0;
        summary.setSamplingRateHz(durationSec > 0 ? (points.size() - 1) / durationSec : 0);

        return summary;
    }

    /** Returns the first (or last) non-zero battery percent reading. */
    private double firstNonZero(List<TelemetryPoint> points, boolean fromStart) {
        if (fromStart) {
            for (TelemetryPoint p : points) {
                if (p.getBatteryPercent() > 0) return p.getBatteryPercent();
            }
        } else {
            for (int i = points.size() - 1; i >= 0; i--) {
                if (points.get(i).getBatteryPercent() > 0) return points.get(i).getBatteryPercent();
            }
        }
        return 0;
    }
}
