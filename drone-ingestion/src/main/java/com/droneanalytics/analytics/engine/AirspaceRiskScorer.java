package com.droneanalytics.analytics.engine;

import com.droneanalytics.analytics.math.HaversineCalculator;
import com.droneanalytics.analytics.model.AirspaceRiskResult;
import com.droneanalytics.analytics.model.AirspaceRiskResult.ZoneProximityEvent;
import com.droneanalytics.ingestion.model.TelemetryPoint;

import java.util.*;

/**
 * Scores a flight's proximity to restricted airspace zones.
 *
 * Uses a static database of controlled airspace zones. In production this
 * would be replaced with live FAA B4UFLY / LAANC API calls.
 *
 * Risk scoring per zone:
 *   Distance > outerBuffer  → 0 risk from this zone
 *   Distance in outerBuffer → linear ramp 0 → 50 (advisory zone)
 *   Distance in innerBuffer → linear ramp 50 → 100 (controlled zone)
 *   Distance = 0 (inside)  → 100
 *
 * The overall flight score is the maximum single-point risk encountered.
 */
public class AirspaceRiskScorer {

    // -------------------------------------------------------------------------
    // Static airspace database
    // -------------------------------------------------------------------------

    private static final List<AirspaceZone> ZONES = new ArrayList<>();

    static {
        // Major US airports (Class B — 5nm radius hard boundary, 30nm advisory)
        ZONES.add(new AirspaceZone("SFO Class B",      "CLASS_B",      37.6213, -122.3790, 9_260,  55_560));
        ZONES.add(new AirspaceZone("LAX Class B",      "CLASS_B",      33.9425, -118.4081, 9_260,  55_560));
        ZONES.add(new AirspaceZone("JFK Class B",      "CLASS_B",      40.6413, -73.7781,  9_260,  55_560));
        ZONES.add(new AirspaceZone("ORD Class B",      "CLASS_B",      41.9742, -87.9073,  9_260,  55_560));
        ZONES.add(new AirspaceZone("DFW Class B",      "CLASS_B",      32.8998, -97.0403,  9_260,  55_560));
        ZONES.add(new AirspaceZone("DEN Class B",      "CLASS_B",      39.8561, -104.6737, 9_260,  55_560));
        ZONES.add(new AirspaceZone("SEA Class B",      "CLASS_B",      47.4502, -122.3088, 9_260,  55_560));
        ZONES.add(new AirspaceZone("MIA Class B",      "CLASS_B",      25.7959, -80.2870,  9_260,  55_560));

        // Class C airports (5nm core, 20nm advisory)
        ZONES.add(new AirspaceZone("SJC Class C",      "CLASS_C",      37.3626, -121.9290, 9_260,  37_040));
        ZONES.add(new AirspaceZone("OAK Class C",      "CLASS_C",      37.7213, -122.2208, 9_260,  37_040));

        // National Parks (FAA UAS restriction 400ft AGL or prohibited)
        ZONES.add(new AirspaceZone("Yosemite NP",      "NATIONAL_PARK", 37.8651, -119.5383, 3_000,  8_000));
        ZONES.add(new AirspaceZone("Grand Canyon NP",  "NATIONAL_PARK", 36.0544, -112.1401, 3_000,  8_000));
        ZONES.add(new AirspaceZone("Yellowstone NP",   "NATIONAL_PARK", 44.4280, -110.5885, 3_000,  8_000));
        ZONES.add(new AirspaceZone("Zion NP",          "NATIONAL_PARK", 37.2982, -113.0263, 3_000,  8_000));

        // Washington DC SFRA (15nm radius no-fly)
        ZONES.add(new AirspaceZone("DC SFRA",          "SFRA",          38.9072, -77.0369,  24_135, 48_270));

        // Stadiums (temporary prohibition during events — use fixed circle)
        ZONES.add(new AirspaceZone("Levi's Stadium",   "STADIUM",       37.4033, -121.9694, 4_800,  9_600));
        ZONES.add(new AirspaceZone("AT&T Park (Oracle)","STADIUM",      37.7786, -122.3893, 4_800,  9_600));
    }

    // -------------------------------------------------------------------------
    // Scoring
    // -------------------------------------------------------------------------

    /**
     * Evaluates airspace risk for each telemetry point against all zones.
     *
     * For performance, only evaluates against zones that could plausibly be
     * nearby (within 100km of the flight's centroid).
     */
    public AirspaceRiskResult score(List<TelemetryPoint> points) {
        if (points == null || points.isEmpty()) {
            AirspaceRiskResult r = new AirspaceRiskResult();
            r.setOverallRiskScore(0);
            r.setProximityEvents(Collections.emptyList());
            return r;
        }

        // Compute flight centroid for zone pre-filtering
        double centLat = points.stream().mapToDouble(TelemetryPoint::getLatitude).average().orElse(0);
        double centLon = points.stream().mapToDouble(TelemetryPoint::getLongitude).average().orElse(0);

        // Filter to zones within 200km of centroid
        List<AirspaceZone> nearbyZones = ZONES.stream()
            .filter(z -> HaversineCalculator.distanceM(centLat, centLon, z.lat, z.lon) < 200_000)
            .toList();

        // Per-zone: find closest approach across all flight points
        Map<AirspaceZone, double[]> closestApproach = new LinkedHashMap<>(); // zone → [dist, lat, lon, ts]
        for (AirspaceZone zone : nearbyZones) {
            double minDist = Double.MAX_VALUE;
            double bestLat = 0, bestLon = 0;
            long bestTs = 0;
            for (TelemetryPoint p : points) {
                double d = HaversineCalculator.distanceM(p.getLatitude(), p.getLongitude(), zone.lat, zone.lon);
                if (d < minDist) {
                    minDist = d; bestLat = p.getLatitude(); bestLon = p.getLongitude(); bestTs = p.getTimestampMs();
                }
            }
            closestApproach.put(zone, new double[]{minDist, bestLat, bestLon, bestTs});
        }

        // Build proximity events for zones that got within outerBuffer
        List<ZoneProximityEvent> events = new ArrayList<>();
        double overallRisk = 0;
        double minDistOverall = Double.MAX_VALUE;
        AirspaceZone nearestZone = null;
        boolean insideAny = false;

        for (Map.Entry<AirspaceZone, double[]> entry : closestApproach.entrySet()) {
            AirspaceZone zone = entry.getKey();
            double[] approach = entry.getValue();
            double dist = approach[0];

            if (dist > zone.outerBufferM) continue; // well clear of this zone

            double risk = computeRisk(dist, zone.innerBufferM, zone.outerBufferM);
            events.add(new ZoneProximityEvent(
                zone.name, zone.type, dist, risk,
                approach[1], approach[2], (long) approach[3]
            ));
            if (risk > overallRisk) overallRisk = risk;
            if (dist < minDistOverall) {
                minDistOverall = dist;
                nearestZone = zone;
            }
            if (dist <= zone.innerBufferM) insideAny = true;
        }

        // Sort events by descending risk
        events.sort((a, b) -> Double.compare(b.getRiskContribution(), a.getRiskContribution()));

        AirspaceRiskResult result = new AirspaceRiskResult();
        result.setOverallRiskScore(overallRisk);
        result.setMinDistanceToRestrictionM(minDistOverall == Double.MAX_VALUE ? Double.NaN : minDistOverall);
        result.setNearestZoneName(nearestZone != null ? nearestZone.name : "None");
        result.setInsideRestrictedZone(insideAny);
        result.setProximityEvents(events);
        return result;
    }

    /**
     * Computes a 0–100 risk score based on distance to a zone.
     *   dist >= outerBuffer → 0
     *   outerBuffer > dist > innerBuffer → linear 0→50
     *   innerBuffer >= dist > 0 → linear 50→100
     *   dist = 0 → 100
     */
    static double computeRisk(double distM, double innerM, double outerM) {
        if (distM >= outerM) return 0;
        if (distM >= innerM) {
            // advisory zone: 0 → 50
            return 50.0 * (1.0 - (distM - innerM) / (outerM - innerM));
        }
        // inside zone: 50 → 100
        return 50.0 + 50.0 * (1.0 - distM / innerM);
    }

    // -------------------------------------------------------------------------
    // Inner: airspace zone record
    // -------------------------------------------------------------------------

    private static class AirspaceZone {
        final String name;
        final String type;
        final double lat;
        final double lon;
        final double innerBufferM; // hard boundary / restricted radius (m)
        final double outerBufferM; // advisory radius (m)

        AirspaceZone(String name, String type, double lat, double lon,
                     double innerBufferM, double outerBufferM) {
            this.name = name; this.type = type;
            this.lat = lat; this.lon = lon;
            this.innerBufferM = innerBufferM;
            this.outerBufferM = outerBufferM;
        }
    }
}
