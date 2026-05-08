package com.droneanalytics.analytics.engine;

import com.droneanalytics.analytics.math.HaversineCalculator;
import com.droneanalytics.analytics.model.AnomalyEvent;
import com.droneanalytics.analytics.model.AnomalyEvent.AnomalyType;
import com.droneanalytics.ingestion.model.TelemetryPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects anomalies in a flight's telemetry stream.
 *
 * Checks performed:
 *
 *   GPS_DRIFT         — consecutive position jump implying speed > MAX_PLAUSIBLE_SPEED_MS
 *                       (typically caused by GPS glitch, multipath, or spoofing)
 *
 *   RAPID_ALTITUDE_DROP — descent rate faster than MAX_DESCENT_RATE_MS
 *                         (potential loss of control or emergency landing)
 *
 *   SIGNAL_LOSS       — timestamp gap between consecutive samples > MAX_GAP_MS
 *                       (telemetry dropout or recording interruption)
 *
 *   BATTERY_ANOMALY   — battery % jumps up between samples (impossible without charging)
 *                       or drops > MAX_BATTERY_DROP_PER_SAMPLE in a single step
 *
 *   IMPOSSIBLE_SPEED  — recorded ground speed exceeds physical limit for consumer drones
 */
public class AnomalyDetector {

    // --- Configurable thresholds ---

    /** Maximum plausible implied speed (m/s) computed from consecutive GPS positions.
     *  Consumer drones: ~80 km/h top speed ≈ 22 m/s; we allow 2× for GPS noise headroom. */
    private double maxPlausibleSpeedMs = 50.0;

    /** Maximum expected descent rate in m/s (free-fall for a 500g drone ≈ 30 m/s). */
    private double maxDescentRateMs = 15.0;

    /** Maximum telemetry gap before flagging signal loss (ms). */
    private double maxGapMs = 5_000;

    /** Maximum battery drop allowed in a single telemetry interval (%). */
    private double maxBatteryDropPerSample = 10.0;

    /** Maximum recorded ground speed in m/s before flagging as impossible. */
    private double maxGroundSpeedMs = 80.0;

    // -------------------------------------------------------------------------

    /**
     * Runs all anomaly checks over the telemetry stream and returns detected events.
     *
     * @param points Chronologically ordered TelemetryPoints.
     * @return List of AnomalyEvents (empty if flight was clean).
     */
    public List<AnomalyEvent> detect(List<TelemetryPoint> points) {
        List<AnomalyEvent> anomalies = new ArrayList<>();
        if (points == null || points.size() < 2) return anomalies;

        for (int i = 1; i < points.size(); i++) {
            TelemetryPoint prev = points.get(i - 1);
            TelemetryPoint curr = points.get(i);

            double dtMs  = curr.getTimestampMs() - prev.getTimestampMs();
            double dtSec = dtMs / 1000.0;

            // 1. Signal loss
            if (dtMs > maxGapMs) {
                anomalies.add(new AnomalyEvent(
                    AnomalyType.SIGNAL_LOSS,
                    curr.getTimestampMs(),
                    curr.getLatitude(), curr.getLongitude(),
                    dtMs / 1000.0, maxGapMs / 1000.0,
                    String.format("Telemetry gap of %.1fs detected", dtMs / 1000.0),
                    "WARNING"
                ));
            }

            if (dtSec <= 0) continue; // skip if timestamps are identical

            // 2. GPS drift — implied speed from position delta
            double posDistM = HaversineCalculator.distanceM(
                prev.getLatitude(), prev.getLongitude(),
                curr.getLatitude(), curr.getLongitude()
            );
            double impliedSpeedMs = posDistM / dtSec;
            if (impliedSpeedMs > maxPlausibleSpeedMs) {
                anomalies.add(new AnomalyEvent(
                    AnomalyType.GPS_DRIFT,
                    curr.getTimestampMs(),
                    curr.getLatitude(), curr.getLongitude(),
                    impliedSpeedMs, maxPlausibleSpeedMs,
                    String.format("Position jumped %.0fm in %.1fs (%.1fm/s implied speed)",
                        posDistM, dtSec, impliedSpeedMs),
                    impliedSpeedMs > maxPlausibleSpeedMs * 3 ? "CRITICAL" : "WARNING"
                ));
            }

            // 3. Rapid altitude drop
            double altDropMs = -(curr.getAltitudeAgl() - prev.getAltitudeAgl()) / dtSec;
            if (altDropMs > maxDescentRateMs) {
                anomalies.add(new AnomalyEvent(
                    AnomalyType.RAPID_ALTITUDE_DROP,
                    curr.getTimestampMs(),
                    curr.getLatitude(), curr.getLongitude(),
                    altDropMs, maxDescentRateMs,
                    String.format("Descent rate %.1fm/s exceeds threshold %.1fm/s",
                        altDropMs, maxDescentRateMs),
                    altDropMs > maxDescentRateMs * 2 ? "CRITICAL" : "WARNING"
                ));
            }

            // 4. Battery anomaly
            double battPrev = prev.getBatteryPercent();
            double battCurr = curr.getBatteryPercent();
            if (battPrev > 0 && battCurr > 0) {
                double battDelta = battCurr - battPrev;
                if (battDelta > 1.0) {
                    // Battery increased — physically impossible
                    anomalies.add(new AnomalyEvent(
                        AnomalyType.BATTERY_ANOMALY,
                        curr.getTimestampMs(),
                        curr.getLatitude(), curr.getLongitude(),
                        battDelta, 0,
                        String.format("Battery increased by %.1f%% (sensor error)", battDelta),
                        "WARNING"
                    ));
                } else if (-battDelta > maxBatteryDropPerSample) {
                    anomalies.add(new AnomalyEvent(
                        AnomalyType.BATTERY_ANOMALY,
                        curr.getTimestampMs(),
                        curr.getLatitude(), curr.getLongitude(),
                        -battDelta, maxBatteryDropPerSample,
                        String.format("Battery dropped %.1f%% in one sample", -battDelta),
                        "WARNING"
                    ));
                }
            }

            // 5. Impossible speed from recorded groundspeed field
            if (curr.getGroundSpeed() > maxGroundSpeedMs) {
                anomalies.add(new AnomalyEvent(
                    AnomalyType.IMPOSSIBLE_SPEED,
                    curr.getTimestampMs(),
                    curr.getLatitude(), curr.getLongitude(),
                    curr.getGroundSpeed(), maxGroundSpeedMs,
                    String.format("Recorded speed %.1fm/s exceeds physical limit %.1fm/s",
                        curr.getGroundSpeed(), maxGroundSpeedMs),
                    "CRITICAL"
                ));
            }
        }

        return anomalies;
    }

    // -------------------------------------------------------------------------
    // Fluent threshold configuration
    // -------------------------------------------------------------------------

    public AnomalyDetector withMaxPlausibleSpeedMs(double v)  { this.maxPlausibleSpeedMs  = v; return this; }
    public AnomalyDetector withMaxDescentRateMs(double v)      { this.maxDescentRateMs      = v; return this; }
    public AnomalyDetector withMaxGapMs(double v)              { this.maxGapMs              = v; return this; }
    public AnomalyDetector withMaxBatteryDropPerSample(double v){ this.maxBatteryDropPerSample = v; return this; }
    public AnomalyDetector withMaxGroundSpeedMs(double v)      { this.maxGroundSpeedMs      = v; return this; }
}
