package com.droneanalytics.analytics.math;

/**
 * Haversine formula — calculates great-circle distance and bearing between
 * two points on the Earth's surface given their WGS84 lat/lon coordinates.
 *
 * The haversine formula is the standard method for drone path analysis because:
 *   - It accounts for Earth's curvature (unlike Euclidean distance)
 *   - It's accurate at the short distances relevant to drone flights
 *   - It's computationally cheap (no ellipsoidal correction needed at <200km)
 */
public class HaversineCalculator {

    /** Earth mean radius in metres (WGS84 semi-major axis approximation). */
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private HaversineCalculator() {} // utility class

    /**
     * Computes the great-circle distance between two lat/lon points in metres.
     *
     * Formula:
     *   a = sin²(Δlat/2) + cos(lat1)·cos(lat2)·sin²(Δlon/2)
     *   c = 2·atan2(√a, √(1−a))
     *   d = R·c
     *
     * @param lat1 Latitude of point 1 (decimal degrees)
     * @param lon1 Longitude of point 1 (decimal degrees)
     * @param lat2 Latitude of point 2 (decimal degrees)
     * @param lon2 Longitude of point 2 (decimal degrees)
     * @return Distance in metres
     */
    public static double distanceM(double lat1, double lon1,
                                   double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double sinDLat = Math.sin(dLat / 2);
        double sinDLon = Math.sin(dLon / 2);

        double a = sinDLat * sinDLat
                 + Math.cos(Math.toRadians(lat1))
                 * Math.cos(Math.toRadians(lat2))
                 * sinDLon * sinDLon;

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    /**
     * Computes the initial bearing from point 1 to point 2, in degrees (0–360).
     *
     * Formula:
     *   θ = atan2(sin(Δlon)·cos(lat2),
     *             cos(lat1)·sin(lat2) − sin(lat1)·cos(lat2)·cos(Δlon))
     *
     * @return Bearing in degrees, 0 = north, 90 = east, clockwise positive
     */
    public static double bearingDeg(double lat1, double lon1,
                                    double lat2, double lon2) {
        double lat1R = Math.toRadians(lat1);
        double lat2R = Math.toRadians(lat2);
        double dLonR = Math.toRadians(lon2 - lon1);

        double y = Math.sin(dLonR) * Math.cos(lat2R);
        double x = Math.cos(lat1R) * Math.sin(lat2R)
                 - Math.sin(lat1R) * Math.cos(lat2R) * Math.cos(dLonR);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }

    /**
     * Returns the absolute angular difference between two headings, normalised to [0, 180].
     */
    public static double headingDifferenceDeg(double hdg1, double hdg2) {
        double diff = Math.abs(hdg1 - hdg2) % 360;
        return diff > 180 ? 360 - diff : diff;
    }
}
