package com.droneanalytics.analytics.model;

import java.util.List;

/**
 * Airspace risk assessment for a flight path.
 *
 * Checks proximity to controlled airspace zones (airports, restricted areas,
 * national parks, temporary flight restrictions) and produces a risk score.
 *
 * Risk score 0–100:
 *   0–25   : Low risk — well clear of controlled airspace
 *   26–50  : Moderate — within advisory distance of a zone
 *   51–75  : High — within the buffer zone of controlled airspace
 *   76–100 : Critical — inside or nearly inside restricted airspace
 */
public class AirspaceRiskResult {

    /** Overall risk score 0–100 (maximum across all points and zones). */
    private double overallRiskScore;

    /** Human-readable risk level: Low | Moderate | High | Critical */
    private String riskLevel;

    /** Minimum distance to any restricted zone during the entire flight (metres). */
    private double minDistanceToRestrictionM;

    /** Name of the nearest airspace zone at closest approach. */
    private String nearestZoneName;

    /** Whether any point in the flight was inside a restricted zone. */
    private boolean insideRestrictedZone;

    /** List of individual zone proximity events along the flight path. */
    private List<ZoneProximityEvent> proximityEvents;

    // -------------------------------------------------------------------------
    // Inner class
    // -------------------------------------------------------------------------

    public static class ZoneProximityEvent {
        private String  zoneName;
        private String  zoneType;       // "CLASS_B", "NATIONAL_PARK", "TFR", "STADIUM", etc.
        private double  distanceM;      // closest approach to this zone (m)
        private double  riskContribution; // 0–100 contribution to overall score
        private double  latitude;       // position of closest approach
        private double  longitude;
        private long    timestampMs;

        public ZoneProximityEvent() {}
        public ZoneProximityEvent(String name, String type, double distM,
                                   double risk, double lat, double lon, long ts) {
            this.zoneName = name; this.zoneType = type; this.distanceM = distM;
            this.riskContribution = risk; this.latitude = lat;
            this.longitude = lon; this.timestampMs = ts;
        }

        public String  getZoneName()         { return zoneName; }
        public String  getZoneType()         { return zoneType; }
        public double  getDistanceM()        { return distanceM; }
        public double  getRiskContribution() { return riskContribution; }
        public double  getLatitude()         { return latitude; }
        public double  getLongitude()        { return longitude; }
        public long    getTimestampMs()      { return timestampMs; }

        @Override
        public String toString() {
            return String.format("ZoneProximity{%s (%s) %.0fm away, risk=%.1f}",
                zoneName, zoneType, distanceM, riskContribution);
        }
    }

    // -------------------------------------------------------------------------
    // Derived
    // -------------------------------------------------------------------------

    public String computeRiskLevel() {
        if (overallRiskScore >= 76) return "Critical";
        if (overallRiskScore >= 51) return "High";
        if (overallRiskScore >= 26) return "Moderate";
        return "Low";
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public double getOverallRiskScore() { return overallRiskScore; }
    public void setOverallRiskScore(double v) { this.overallRiskScore = v; this.riskLevel = computeRiskLevel(); }

    public String getRiskLevel() { return riskLevel; }

    public double getMinDistanceToRestrictionM() { return minDistanceToRestrictionM; }
    public void setMinDistanceToRestrictionM(double v) { this.minDistanceToRestrictionM = v; }

    public String getNearestZoneName() { return nearestZoneName; }
    public void setNearestZoneName(String v) { this.nearestZoneName = v; }

    public boolean isInsideRestrictedZone() { return insideRestrictedZone; }
    public void setInsideRestrictedZone(boolean v) { this.insideRestrictedZone = v; }

    public List<ZoneProximityEvent> getProximityEvents() { return proximityEvents; }
    public void setProximityEvents(List<ZoneProximityEvent> v) { this.proximityEvents = v; }

    @Override
    public String toString() {
        return String.format("AirspaceRisk{score=%.1f (%s), minDist=%.0fm to '%s', inside=%b, events=%d}",
            overallRiskScore, riskLevel, minDistanceToRestrictionM,
            nearestZoneName, insideRestrictedZone,
            proximityEvents != null ? proximityEvents.size() : 0);
    }
}
