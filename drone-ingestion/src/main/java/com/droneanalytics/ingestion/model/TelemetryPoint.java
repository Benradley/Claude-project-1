package com.droneanalytics.ingestion.model;

/**
 * Unified telemetry snapshot — the single internal currency of the analytics pipeline.
 * Every parser normalizes its format-specific fields into this model.
 * All units are SI: meters, m/s, degrees, volts.
 */
public class TelemetryPoint {

    /** Unix time in milliseconds. */
    private long timestampMs;

    /** WGS84 latitude in decimal degrees. */
    private double latitude;

    /** WGS84 longitude in decimal degrees. */
    private double longitude;

    /** Altitude above ground level (AGL) in meters. */
    private double altitudeAgl;

    /** Altitude above mean sea level (MSL) in meters. */
    private double altitudeMsl;

    /** Velocity north component in m/s (NED frame). */
    private double velocityNorth;

    /** Velocity east component in m/s (NED frame). */
    private double velocityEast;

    /** Velocity down component in m/s (NED frame, positive = descending). */
    private double velocityDown;

    /**
     * Ground speed in m/s.
     * Derived as sqrt(vN^2 + vE^2) when not provided directly.
     */
    private double groundSpeed;

    /** Heading in degrees, 0-360 (0 = north). */
    private double headingDeg;

    /** Roll angle in degrees (positive = right wing down). */
    private double rollDeg;

    /** Pitch angle in degrees (positive = nose up). */
    private double pitchDeg;

    /** Battery pack voltage in volts. */
    private double batteryVoltage;

    /** Remaining battery percentage, 0-100. */
    private double batteryPercent;

    /** Flight mode string (e.g. "GPS", "STABILIZE", "AUTO", "LOITER"). */
    private String flightMode;

    /** Source drone type / autopilot system. */
    private DroneType droneType;

    /** Whether the drone had a valid GPS fix at this sample. */
    private boolean hasGpsFix;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public TelemetryPoint() {}

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public long getTimestampMs() { return timestampMs; }
    public void setTimestampMs(long timestampMs) { this.timestampMs = timestampMs; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getAltitudeAgl() { return altitudeAgl; }
    public void setAltitudeAgl(double altitudeAgl) { this.altitudeAgl = altitudeAgl; }

    public double getAltitudeMsl() { return altitudeMsl; }
    public void setAltitudeMsl(double altitudeMsl) { this.altitudeMsl = altitudeMsl; }

    public double getVelocityNorth() { return velocityNorth; }
    public void setVelocityNorth(double velocityNorth) { this.velocityNorth = velocityNorth; }

    public double getVelocityEast() { return velocityEast; }
    public void setVelocityEast(double velocityEast) { this.velocityEast = velocityEast; }

    public double getVelocityDown() { return velocityDown; }
    public void setVelocityDown(double velocityDown) { this.velocityDown = velocityDown; }

    public double getGroundSpeed() { return groundSpeed; }
    public void setGroundSpeed(double groundSpeed) { this.groundSpeed = groundSpeed; }

    public double getHeadingDeg() { return headingDeg; }
    public void setHeadingDeg(double headingDeg) { this.headingDeg = headingDeg; }

    public double getRollDeg() { return rollDeg; }
    public void setRollDeg(double rollDeg) { this.rollDeg = rollDeg; }

    public double getPitchDeg() { return pitchDeg; }
    public void setPitchDeg(double pitchDeg) { this.pitchDeg = pitchDeg; }

    public double getBatteryVoltage() { return batteryVoltage; }
    public void setBatteryVoltage(double batteryVoltage) { this.batteryVoltage = batteryVoltage; }

    public double getBatteryPercent() { return batteryPercent; }
    public void setBatteryPercent(double batteryPercent) { this.batteryPercent = batteryPercent; }

    public String getFlightMode() { return flightMode; }
    public void setFlightMode(String flightMode) { this.flightMode = flightMode; }

    public DroneType getDroneType() { return droneType; }
    public void setDroneType(DroneType droneType) { this.droneType = droneType; }

    public boolean isHasGpsFix() { return hasGpsFix; }
    public void setHasGpsFix(boolean hasGpsFix) { this.hasGpsFix = hasGpsFix; }

    @Override
    public String toString() {
        return String.format(
            "TelemetryPoint{t=%d, lat=%.6f, lon=%.6f, agl=%.1fm, spd=%.1fm/s, hdg=%.1f, bat=%.0f%%, type=%s}",
            timestampMs, latitude, longitude, altitudeAgl, groundSpeed, headingDeg, batteryPercent, droneType
        );
    }
}
