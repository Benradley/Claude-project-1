package com.droneanalytics.analytics.model;

/**
 * A single detected anomaly in the flight telemetry stream.
 */
public class AnomalyEvent {

    public enum AnomalyType {
        /** Sudden large position jump — GPS glitch or spoofing. */
        GPS_DRIFT,
        /** Rapid uncontrolled descent rate exceeding threshold. */
        RAPID_ALTITUDE_DROP,
        /** Gap in telemetry larger than expected — signal loss or recording gap. */
        SIGNAL_LOSS,
        /** Battery percentage spiked up or dropped suddenly — sensor error. */
        BATTERY_ANOMALY,
        /** Ground speed physically impossible given the aircraft specs. */
        IMPOSSIBLE_SPEED
    }

    private AnomalyType type;
    private long        timestampMs;   // when the anomaly was detected
    private double      latitude;      // position at detection
    private double      longitude;
    private double      measuredValue; // the outlier value that triggered detection
    private double      threshold;     // the limit that was exceeded
    private String      description;   // human-readable explanation
    private String      severity;      // "WARNING" or "CRITICAL"

    public AnomalyEvent() {}

    public AnomalyEvent(AnomalyType type, long timestampMs, double lat, double lon,
                        double measured, double threshold, String description, String severity) {
        this.type = type;
        this.timestampMs = timestampMs;
        this.latitude = lat;
        this.longitude = lon;
        this.measuredValue = measured;
        this.threshold = threshold;
        this.description = description;
        this.severity = severity;
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public AnomalyType getType() { return type; }
    public void setType(AnomalyType v) { this.type = v; }

    public long getTimestampMs() { return timestampMs; }
    public void setTimestampMs(long v) { this.timestampMs = v; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double v) { this.latitude = v; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double v) { this.longitude = v; }

    public double getMeasuredValue() { return measuredValue; }
    public void setMeasuredValue(double v) { this.measuredValue = v; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double v) { this.threshold = v; }

    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }

    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity = v; }

    @Override
    public String toString() {
        return String.format("[%s] %s @ t=%dms (%.4f,%.4f): %s (measured=%.2f, threshold=%.2f)",
            severity, type, timestampMs, latitude, longitude,
            description, measuredValue, threshold);
    }
}
