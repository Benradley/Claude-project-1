package com.droneanalytics.service;

import com.droneanalytics.api.dto.AnalysisReportDto;
import com.droneanalytics.api.dto.FlightSessionDto;
import com.droneanalytics.entity.FlightSession;
import com.droneanalytics.entity.User;
import com.droneanalytics.repository.FlightSessionRepository;
import com.droneanalytics.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Persists and retrieves flight sessions (Step 8 — FR persistence layer).
 *
 * <p>The full {@link AnalysisReportDto} is serialised to JSON and stored in
 * {@link FlightSession#reportJson}.  Denormalised summary fields let the list
 * endpoint avoid deserialising every row.
 */
@Service
public class FlightSessionService {

    private static final Logger log = LoggerFactory.getLogger(FlightSessionService.class);

    private final FlightSessionRepository sessionRepo;
    private final UserRepository          userRepo;
    private final ObjectMapper            objectMapper;

    public FlightSessionService(FlightSessionRepository sessionRepo,
                                UserRepository userRepo,
                                ObjectMapper objectMapper) {
        this.sessionRepo  = sessionRepo;
        this.userRepo     = userRepo;
        this.objectMapper = objectMapper;
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    /**
     * Persists a completed analysis.  If the user is not found (e.g. mock users
     * in tests) the call is silently skipped — the analyze endpoint still succeeds.
     *
     * @return the saved entity id, or {@code -1} if skipped
     */
    @Transactional
    public long save(String userEmail, String filename, AnalysisReportDto dto) {
        Optional<User> userOpt = userRepo.findByEmail(userEmail);
        if (userOpt.isEmpty()) {
            log.warn("Skipping session save — no user found for email: {}", userEmail);
            return -1L;
        }

        String reportJson;
        try {
            reportJson = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("Could not serialise AnalysisReportDto for session save", e);
            return -1L;
        }

        FlightSession session = new FlightSession();
        session.setUser(userOpt.get());
        session.setFilename(filename != null ? filename : "unknown");
        session.setUploadedAt(Instant.now());
        session.setReportJson(reportJson);

        // Denormalised summary
        session.setDroneType(dto.droneType);
        session.setHealthy(dto.healthy);
        if (dto.summary != null) {
            session.setDurationSeconds(dto.summary.durationSeconds);
            session.setTotalDistanceM(dto.summary.totalDistanceM);
        }
        session.setAnomalyCount(dto.anomalies != null ? dto.anomalies.size() : 0);
        if (dto.airspaceRisk != null) {
            session.setRiskLevel(dto.airspaceRisk.riskLevel);
        }

        return sessionRepo.save(session).getId();
    }

    // ── List ─────────────────────────────────────────────────────────────────

    /**
     * Returns a page of flight-session summaries for the authenticated user,
     * newest first.  Default page size is 20.
     */
    @Transactional(readOnly = true)
    public Page<FlightSession> list(String userEmail, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return sessionRepo.findByUserEmailOrderByUploadedAtDesc(userEmail, pageable);
    }

    // ── Get full report ───────────────────────────────────────────────────────

    /**
     * Retrieves the full {@link AnalysisReportDto} for a session.
     *
     * @return empty if the session does not exist or belongs to a different user
     */
    @Transactional(readOnly = true)
    public Optional<AnalysisReportDto> getReport(long sessionId, String userEmail) {
        return sessionRepo.findByIdAndUserEmail(sessionId, userEmail)
            .map(session -> {
                try {
                    return objectMapper.readValue(session.getReportJson(), AnalysisReportDto.class);
                } catch (JsonProcessingException e) {
                    log.error("Could not deserialise reportJson for session {}", sessionId, e);
                    return null;
                }
            });
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes a session only if it belongs to the requesting user.
     *
     * @return {@code true} if deleted, {@code false} if not found / wrong owner
     */
    @Transactional
    public boolean delete(long sessionId, String userEmail) {
        return sessionRepo.findByIdAndUserEmail(sessionId, userEmail)
            .map(session -> { sessionRepo.delete(session); return true; })
            .orElse(false);
    }
}
