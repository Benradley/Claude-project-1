package com.droneanalytics.notification;

import com.droneanalytics.analytics.model.AnomalyEvent;
import com.droneanalytics.analytics.model.AnalysisReport;
import com.droneanalytics.analytics.model.AirspaceRiskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Notification service for flight anomaly alerts (FR-6).
 *
 * <p>Two delivery channels are supported:
 * <ol>
 *   <li><b>In-app log</b> — every alert is appended to an in-memory
 *       {@link AppNotification} list readable via {@link #getNotifications()}.
 *       A database-backed equivalent would replace this in production.</li>
 *   <li><b>Outbound webhook</b> — when {@code drone.notification.webhook.enabled=true}
 *       a POST request carrying a JSON payload is sent to the configured URL.
 *       Compatible with Slack incoming webhooks, Zapier, Make, and any custom HTTP
 *       endpoint that accepts JSON.</li>
 * </ol>
 *
 * <p>The service fires one notification per critical anomaly and one summary
 * notification if the airspace risk level is HIGH or CRITICAL.
 *
 * <p>All dispatch is synchronous. In production this would be replaced with an
 * async queue (Spring @Async / Spring Events / a message broker).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    // ── Configuration (from application.properties / env vars) ───────────────

    @Value("${drone.notification.webhook.enabled:false}")
    private boolean webhookEnabled;

    @Value("${drone.notification.webhook.url:}")
    private String webhookUrl;

    // ── In-app notification store (in-memory — replace with DB in production) ─

    private final List<AppNotification> notifications =
        Collections.synchronizedList(new ArrayList<>());

    // ── HTTP client for outbound webhooks ─────────────────────────────────────

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Inspects {@code report} for anomalies and elevated airspace risk,
     * then fires notifications for any issues found.
     *
     * @param report   Completed analysis report.
     * @param filename Original upload filename (used in notification messages).
     */
    public void processReport(AnalysisReport report, String filename) {
        if (report == null) return;

        String label = filename != null ? filename : "unknown file";

        // ── 1. Anomaly notifications ──────────────────────────────────────────
        if (report.getAnomalies() != null) {
            for (AnomalyEvent event : report.getAnomalies()) {
                String severity = event.getSeverity();
                if ("HIGH".equalsIgnoreCase(severity) || "CRITICAL".equalsIgnoreCase(severity)) {
                    String title   = "Flight Anomaly Detected — " + event.getType();
                    String message = String.format(
                        "[%s] %s anomaly in %s at %s: %s (measured=%.2f, threshold=%.2f)",
                        severity,
                        event.getType(),
                        label,
                        TS_FMT.format(Instant.ofEpochMilli(event.getTimestampMs())),
                        event.getDescription(),
                        event.getMeasuredValue(),
                        event.getThreshold()
                    );
                    dispatch(title, message, severity, label);
                }
            }
        }

        // ── 2. Airspace risk notification ─────────────────────────────────────
        AirspaceRiskResult air = report.getAirspaceRisk();
        if (air != null) {
            String level = air.getRiskLevel();
            if ("HIGH".equalsIgnoreCase(level) || "CRITICAL".equalsIgnoreCase(level)) {
                String title   = "Airspace Risk Alert — " + level;
                String message = String.format(
                    "[%s] Flight in %s entered %s risk airspace. Score=%.1f/100. " +
                    "Nearest zone: %s",
                    level, label, level,
                    air.getOverallRiskScore(),
                    air.getNearestZoneName() != null ? air.getNearestZoneName() : "unknown"
                );
                if (air.isInsideRestrictedZone()) {
                    message += " — INSIDE RESTRICTED ZONE (VIOLATION)";
                }
                dispatch(title, message, level, label);
            }
        }
    }

    /**
     * Returns an unmodifiable view of all in-app notifications generated this session.
     */
    public List<AppNotification> getNotifications() {
        return Collections.unmodifiableList(notifications);
    }

    /**
     * Clears the in-app notification list (useful for testing).
     */
    public void clearNotifications() {
        notifications.clear();
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    private void dispatch(String title, String message, String severity, String source) {
        // Always record in-app
        AppNotification notification = new AppNotification(title, message, severity, source);
        notifications.add(notification);
        log.warn("[NOTIFICATION] {} | {}", title, message);

        // Optionally send webhook
        if (webhookEnabled && webhookUrl != null && !webhookUrl.isBlank()) {
            sendWebhook(notification);
        }
    }

    /**
     * Sends a JSON POST to the configured webhook URL.
     *
     * Payload format is compatible with Slack incoming webhooks (text field)
     * and general-purpose webhook receivers:
     * <pre>
     * {
     *   "text": "...",
     *   "title": "...",
     *   "severity": "HIGH",
     *   "source": "flight.bin",
     *   "timestamp": "2024-01-01 12:00:00"
     * }
     * </pre>
     */
    private void sendWebhook(AppNotification n) {
        String json = String.format(
            "{\"text\":\"%s\",\"title\":\"%s\",\"severity\":\"%s\",\"source\":\"%s\",\"timestamp\":\"%s\"}",
            escapeJson(n.getMessage()),
            escapeJson(n.getTitle()),
            escapeJson(n.getSeverity()),
            escapeJson(n.getSource()),
            TS_FMT.format(n.getTimestamp())
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Webhook delivered (HTTP {}): {}", response.statusCode(), n.getTitle());
            } else {
                log.warn("Webhook returned non-2xx status {}: {}", response.statusCode(), n.getTitle());
            }
        } catch (IOException | InterruptedException ex) {
            log.error("Webhook delivery failed for '{}': {}", n.getTitle(), ex.getMessage());
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // ── In-app notification model ─────────────────────────────────────────────

    /**
     * Immutable record of a single in-app notification.
     *
     * <p>In a production system this would be a JPA entity persisted to a
     * notifications table, with read/unread state and per-user delivery.
     */
    public static class AppNotification {
        private final String  title;
        private final String  message;
        private final String  severity;
        private final String  source;
        private final Instant timestamp;

        public AppNotification(String title, String message, String severity, String source) {
            this.title     = title;
            this.message   = message;
            this.severity  = severity;
            this.source    = source;
            this.timestamp = Instant.now();
        }

        public String  getTitle()     { return title; }
        public String  getMessage()   { return message; }
        public String  getSeverity()  { return severity; }
        public String  getSource()    { return source; }
        public Instant getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("[%s] %s — %s (%s)", severity, title, source,
                TS_FMT.format(timestamp));
        }
    }
}
