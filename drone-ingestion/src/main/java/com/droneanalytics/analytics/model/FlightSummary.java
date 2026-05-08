package com.droneanalytics.analytics.model;

/**
 * Aggregate statistics for a single flight.
 * Produced by StatisticsCalculator from a list of TelemetryPoints.
 */
public class FlightSummary {

    private long flightDurationMs;        // total flight time
    private double totalDistanceM;        // accumulated ground track distance (m)
    private double maxAltitudeAglM;       // peak altitude above ground level (m)
    private double avgGroundSpeedMs;      // average ground speed (m/s)
    private double maxGroundSpeedMs;      // peak ground speed (m/s)
    private double batteryDrainPct;       // battery % consumed over the flight
    private int    totalPoints;           // number of telemetry samples
    private double samplingRateHz;        // average sampling frequency
    private long   startTimestampMs;      // Unix ms of first sample
    private long   endTimestampMs;        // Unix ms of last sample
    private double startLatitude;
    private double startLongitude;
    private double endLatitude;
    private double endLongitude;
    private double straightLineDistanceM; // haversine start→end (m)

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public long getFlightDurationMs() { return flightDurationMs; }
    public void setFlightDurationMs(long v) { this.flightDurationMs = v; }

    public double getTotalDistanceM() { return totalDistanceM; }
    public void setTotalDistanceM(double v) { this.totalDistanceM = v; }

    public double getMaxAltitudeAglM() { return maxAltitudeAglM; }
    public void setMaxAltitudeAglM(double v) { this.maxAltitudeAglM = v; }

    public double getAvgGroundSpeedMs() { return avgGroundSpeedMs; }
    public void setAvgGroundSpeedMs(double v) { this.avgGroundSpeedMs = v; }

    public double getMaxGroundSpeedMs() { return maxGroundSpeedMs; }
    public void setMaxGroundSpeedMs(double v) { this.maxGroundSpeedMs = v; }

    public double getBatteryDrainPct() { return batteryDrainPct; }
    public void setBatteryDrainPct(double v) { this.batteryDrainPct = v; }

    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int v) { this.totalPoints = v; }

    public double getSamplingRateHz() { return samplingRateHz; }
    public void setSamplingRateHz(double v) { this.samplingRateHz = v; }

    public long getStartTimestampMs() { return startTimestampMs; }
    public void setStartTimestampMs(long v) { this.startTimestampMs = v; }

    public long getEndTimestampMs() { return endTimestampMs; }
    public void setEndTimestampMs(long v) { this.endTimestampMs = v; }

    public double getStartLatitude() { return startLatitude; }
    public void setStartLatitude(double v) { this.startLatitude = v; }

    public double getStartLongitude() { return startLongitude; }
    public void setStartLongitude(double v) { this.startLongitude = v; }

    public double getEndLatitude() { return endLatitude; }
    public void setEndLatitude(double v) { this.endLatitude = v; }

    public double getEndLongitude() { return endLongitude; }
    public void setEndLongitude(double v) { this.endLongitude = v; }

    public double getStraightLineDistanceM() { return straightLineDistanceM; }
    public void setStraightLineDistanceM(double v) { this.straightLineDistanceM = v; }

    @Override
    public String toString() {
        return String.format(
            "FlightSummary{duration=%.1fs, dist=%.0fm, maxAGL=%.1fm, " +
            "avgSpd=%.1fm/s, maxSpd=%.1fm/s, battDrain=%.1f%%, points=%d}",
            flightDurationMs / 1000.0, totalDistanceM, maxAltitudeAglM,
            avgGroundSpeedMs, maxGroundSpeedMs, batteryDrainPct, totalPoints
        );
    }
}
