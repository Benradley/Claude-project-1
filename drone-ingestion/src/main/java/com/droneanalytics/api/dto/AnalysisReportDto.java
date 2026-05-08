package com.droneanalytics.api.dto;

import com.droneanalytics.analytics.model.*;
import com.droneanalytics.ingestion.model.DroneType;
import com.droneanalytics.ingestion.model.TelemetryPoint;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JSON-friendly Data Transfer Object for AnalysisReport.
 * Flattens nested model objects into clean API response shapes.
 */
public class AnalysisReportDto {

    public Long      sessionId;   // set after DB save; null when not persisted
    public String    droneType;
    public boolean   healthy;
    public String    statusLine;
    public Summary   summary;
    public Path      pathOptimization;
    public Battery   battery;
    public List<Anomaly> anomalies;
    public Airspace  airspaceRisk;
    public List<TelemetryDto> telemetry;

    // ── Nested DTOs ──────────────────────────────────────────────────────────

    public static class Summary {
        public double durationSeconds;
        public double totalDistanceM;
        public double straightLineDistanceM;
        public double maxAltitudeAglM;
        public double avgGroundSpeedMs;
        public double maxGroundSpeedMs;
        public double batteryDrainPct;
        public int    totalPoints;
        public double samplingRateHz;
        public long   startTimestampMs;
        public long   endTimestampMs;
    }

    public static class Path {
        public double greatCircleDistanceM;
        public double actualDistanceM;
        public double efficiencyScore;
        public String efficiencyRating;
        public double wastedDistanceM;
        public double meanBearingDeviationDeg;
        public double maxBearingDeviationDeg;
    }

    public static class Battery {
        public double   rSquared;
        public double[] coefficients;
        public double   initialBatteryPct;
        public double   finalBatteryPct;
        public double   totalDrainPct;
        public double   avgDischargeRatePerMin;
        public double   predictedLowBatteryTimeSec;
        public double   predictedCriticalBatteryTimeSec;
        public double   batteryPercentPerKm;
    }

    public static class Anomaly {
        public String type;
        public long   timestampMs;
        public double latitude;
        public double longitude;
        public double measuredValue;
        public double threshold;
        public String description;
        public String severity;
    }

    public static class Airspace {
        public double overallRiskScore;
        public String riskLevel;
        public double minDistanceToRestrictionM;
        public String nearestZoneName;
        public boolean insideRestrictedZone;
        public List<ProximityEvent> proximityEvents;
    }

    public static class ProximityEvent {
        public String zoneName;
        public String zoneType;
        public double distanceM;
        public double riskContribution;
    }

    /** Lightweight telemetry snapshot sent to the dashboard for map + chart rendering. */
    public static class TelemetryDto {
        public long   timestampMs;
        public double latitude;
        public double longitude;
        public double altitudeAglM;
        public double altitudeMslM;
        public double groundSpeedMs;
        public double headingDeg;
        public double batteryPct;
        public double batteryV;
        public double rollDeg;
        public double pitchDeg;
        public String flightMode;
        public boolean hasGpsFix;
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    /** Convenience overload — no telemetry (backward-compatible). */
    public static AnalysisReportDto from(com.droneanalytics.analytics.model.AnalysisReport report) {
        return from(report, null);
    }

    public static AnalysisReportDto from(com.droneanalytics.analytics.model.AnalysisReport report,
                                          List<TelemetryPoint> points) {
        AnalysisReportDto dto = new AnalysisReportDto();
        dto.droneType  = report.getDroneType() != null ? report.getDroneType().name() : "UNKNOWN";
        dto.healthy    = report.isFlightHealthy();
        dto.statusLine = report.getStatusLine();

        // Summary
        FlightSummary s = report.getSummary();
        if (s != null) {
            dto.summary = new Summary();
            dto.summary.durationSeconds        = s.getFlightDurationMs() / 1000.0;
            dto.summary.totalDistanceM         = s.getTotalDistanceM();
            dto.summary.straightLineDistanceM  = s.getStraightLineDistanceM();
            dto.summary.maxAltitudeAglM        = s.getMaxAltitudeAglM();
            dto.summary.avgGroundSpeedMs       = s.getAvgGroundSpeedMs();
            dto.summary.maxGroundSpeedMs       = s.getMaxGroundSpeedMs();
            dto.summary.batteryDrainPct        = s.getBatteryDrainPct();
            dto.summary.totalPoints            = s.getTotalPoints();
            dto.summary.samplingRateHz         = s.getSamplingRateHz();
            dto.summary.startTimestampMs       = s.getStartTimestampMs();
            dto.summary.endTimestampMs         = s.getEndTimestampMs();
        }

        // Path
        PathOptimizationResult p = report.getPathOptimization();
        if (p != null) {
            dto.pathOptimization = new Path();
            dto.pathOptimization.greatCircleDistanceM   = p.getGreatCircleDistanceM();
            dto.pathOptimization.actualDistanceM        = p.getActualDistanceM();
            dto.pathOptimization.efficiencyScore        = p.getEfficiencyScore();
            dto.pathOptimization.efficiencyRating       = p.getEfficiencyRating();
            dto.pathOptimization.wastedDistanceM        = p.getWastedDistanceM();
            dto.pathOptimization.meanBearingDeviationDeg = p.getMeanBearingDeviationDeg();
            dto.pathOptimization.maxBearingDeviationDeg  = p.getMaxBearingDeviationDeg();
        }

        // Battery
        BatteryModel b = report.getBatteryModel();
        if (b != null) {
            dto.battery = new Battery();
            dto.battery.rSquared                      = b.getRSquared();
            dto.battery.coefficients                  = b.getCoefficients();
            dto.battery.initialBatteryPct             = b.getInitialBatteryPct();
            dto.battery.finalBatteryPct               = b.getFinalBatteryPct();
            dto.battery.totalDrainPct                 = b.getTotalDrainPct();
            dto.battery.avgDischargeRatePerMin        = b.getAvgDischargeRatePerMin();
            dto.battery.predictedLowBatteryTimeSec    = b.getPredictedLowBatteryTimeSec();
            dto.battery.predictedCriticalBatteryTimeSec = b.getPredictedCriticalBatteryTimeSec();
            dto.battery.batteryPercentPerKm           = b.getBatteryPercentPerKm();
        }

        // Anomalies
        if (report.getAnomalies() != null) {
            dto.anomalies = report.getAnomalies().stream().map(a -> {
                Anomaly ad = new Anomaly();
                ad.type         = a.getType().name();
                ad.timestampMs  = a.getTimestampMs();
                ad.latitude     = a.getLatitude();
                ad.longitude    = a.getLongitude();
                ad.measuredValue = a.getMeasuredValue();
                ad.threshold    = a.getThreshold();
                ad.description  = a.getDescription();
                ad.severity     = a.getSeverity();
                return ad;
            }).collect(Collectors.toList());
        }

        // Airspace
        AirspaceRiskResult air = report.getAirspaceRisk();
        if (air != null) {
            dto.airspaceRisk = new Airspace();
            dto.airspaceRisk.overallRiskScore         = air.getOverallRiskScore();
            dto.airspaceRisk.riskLevel                = air.getRiskLevel();
            dto.airspaceRisk.minDistanceToRestrictionM = air.getMinDistanceToRestrictionM();
            dto.airspaceRisk.nearestZoneName          = air.getNearestZoneName();
            dto.airspaceRisk.insideRestrictedZone     = air.isInsideRestrictedZone();
            if (air.getProximityEvents() != null) {
                dto.airspaceRisk.proximityEvents = air.getProximityEvents().stream().map(e -> {
                    ProximityEvent pe = new ProximityEvent();
                    pe.zoneName         = e.getZoneName();
                    pe.zoneType         = e.getZoneType();
                    pe.distanceM        = e.getDistanceM();
                    pe.riskContribution = e.getRiskContribution();
                    return pe;
                }).collect(Collectors.toList());
            }
        }

        // Telemetry points (only present when raw points are passed in)
        if (points != null && !points.isEmpty()) {
            dto.telemetry = points.stream().map(tp -> {
                TelemetryDto t = new TelemetryDto();
                t.timestampMs   = tp.getTimestampMs();
                t.latitude      = tp.getLatitude();
                t.longitude     = tp.getLongitude();
                t.altitudeAglM  = tp.getAltitudeAgl();
                t.altitudeMslM  = tp.getAltitudeMsl();
                t.groundSpeedMs = tp.getGroundSpeed();
                t.headingDeg    = tp.getHeadingDeg();
                t.batteryPct    = tp.getBatteryPercent();
                t.batteryV      = tp.getBatteryVoltage();
                t.rollDeg       = tp.getRollDeg();
                t.pitchDeg      = tp.getPitchDeg();
                t.flightMode    = tp.getFlightMode();
                t.hasGpsFix     = tp.isHasGpsFix();
                return t;
            }).collect(Collectors.toList());
        }

        return dto;
    }
}
