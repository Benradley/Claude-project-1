package com.droneanalytics.api.controller;

import com.droneanalytics.analytics.engine.AnalyticsEngine;
import com.droneanalytics.analytics.model.AnalysisReport;
import com.droneanalytics.api.dto.AnalysisReportDto;
import com.droneanalytics.api.dto.FlightSessionDto;
import com.droneanalytics.entity.FlightSession;
import com.droneanalytics.ingestion.FlightLogIngestionService;
import com.droneanalytics.ingestion.model.TelemetryPoint;
import com.droneanalytics.notification.NotificationService;
import com.droneanalytics.reporting.ReportExporter;
import com.droneanalytics.service.FlightSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller — flight log upload, analysis, exports, and history (FR-2/3/5/8).
 *
 * Endpoints:
 *   GET  /api/health                  — liveness probe (public)
 *   POST /api/flights/analyze         — upload log → JSON analysis + saves session
 *   POST /api/flights/report/csv      — upload log → CSV download
 *   POST /api/flights/report/pdf      — upload log → PDF download
 *   GET  /api/flights                 — list authenticated user's past sessions
 *   GET  /api/flights/{id}            — retrieve full report for a past session
 *   DELETE /api/flights/{id}          — delete a past session
 */
@RestController
@RequestMapping("/api")
public class FlightController {

    private static final Logger log = LoggerFactory.getLogger(FlightController.class);

    private final AnalyticsEngine          analyticsEngine;
    private final FlightLogIngestionService ingestionService;
    private final ReportExporter           reportExporter;
    private final NotificationService      notificationService;
    private final FlightSessionService     sessionService;

    public FlightController(AnalyticsEngine analyticsEngine,
                            FlightLogIngestionService ingestionService,
                            ReportExporter reportExporter,
                            NotificationService notificationService,
                            FlightSessionService sessionService) {
        this.analyticsEngine    = analyticsEngine;
        this.ingestionService   = ingestionService;
        this.reportExporter     = reportExporter;
        this.notificationService = notificationService;
        this.sessionService     = sessionService;
    }

    // ── Health ────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Drone Analytics API is running");
    }

    // ── Analyze ───────────────────────────────────────────────────────────────

    /**
     * Uploads a drone log, runs the analytics pipeline, saves the session,
     * and returns the full JSON report (including telemetry for the dashboard).
     */
    @PostMapping("/flights/analyze")
    public ResponseEntity<?> analyze(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal) {

        if (file.isEmpty()) return ResponseEntity.badRequest().body("No file uploaded");

        try {
            List<TelemetryPoint> points = ingestionService.ingest(
                file.getBytes(), file.getOriginalFilename());

            AnalysisReport report = analyticsEngine.analyze(points);
            AnalysisReportDto dto  = AnalysisReportDto.from(report, points);

            // Notifications (anomaly alerts, airspace warnings)
            notificationService.processReport(report, file.getOriginalFilename());

            // Persist session — fails silently for mock/test users
            if (principal != null) {
                try {
                    long sessionId = sessionService.save(
                        principal.getUsername(), file.getOriginalFilename(), dto);
                    if (sessionId > 0) dto.sessionId = sessionId;
                } catch (Exception e) {
                    log.warn("Session save failed (non-fatal): {}", e.getMessage());
                }
            }

            return ResponseEntity.ok(dto);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body("Could not process log file: " + e.getMessage());
        }
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    @PostMapping("/flights/report/csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().build();
        try {
            List<TelemetryPoint> points = ingestionService.ingest(
                file.getBytes(), file.getOriginalFilename());
            AnalysisReport report = analyticsEngine.analyze(points);

            byte[] csv = reportExporter.telemetryToCsv(points, report);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + baseName(file.getOriginalFilename()) + "_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        }
    }

    // ── PDF export ────────────────────────────────────────────────────────────

    @PostMapping("/flights/report/pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().build();
        try {
            List<TelemetryPoint> points = ingestionService.ingest(
                file.getBytes(), file.getOriginalFilename());
            AnalysisReport report = analyticsEngine.analyze(points);

            byte[] pdf = reportExporter.toPdf(report);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + baseName(file.getOriginalFilename()) + "_report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        }
    }

    // ── Flight history ────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of the authenticated user's past flights.
     *
     * Query params: page (default 0), size (default 20, max 100)
     */
    @GetMapping("/flights")
    public ResponseEntity<?> listSessions(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<FlightSession> sessions =
            sessionService.list(principal.getUsername(), page, size);

        Map<String, Object> body = new HashMap<>();
        body.put("sessions",    FlightSessionDto.fromList(sessions.getContent()));
        body.put("totalCount",  sessions.getTotalElements());
        body.put("totalPages",  sessions.getTotalPages());
        body.put("currentPage", sessions.getNumber());
        return ResponseEntity.ok(body);
    }

    /**
     * Returns the full {@link AnalysisReportDto} for a saved session.
     * Returns 404 if the session does not exist or belongs to another user.
     */
    @GetMapping("/flights/{id}")
    public ResponseEntity<?> getSession(
            @PathVariable long id,
            @AuthenticationPrincipal UserDetails principal) {

        return sessionService.getReport(id, principal.getUsername())
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /** Deletes a session that belongs to the authenticated user. */
    @DeleteMapping("/flights/{id}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable long id,
            @AuthenticationPrincipal UserDetails principal) {

        return sessionService.delete(id, principal.getUsername())
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String baseName(String filename) {
        if (filename == null) return "flight";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
