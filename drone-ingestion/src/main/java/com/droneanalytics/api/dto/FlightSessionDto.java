package com.droneanalytics.api.dto;

import com.droneanalytics.entity.FlightSession;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Lightweight summary DTO for listing a user's past flights.
 * The full {@link AnalysisReportDto} is returned separately via GET /api/flights/{id}.
 */
public class FlightSessionDto {

    public Long    id;
    public String  filename;
    public String  uploadedAt;   // ISO-8601 UTC
    public String  droneType;
    public boolean healthy;
    public Double  durationSeconds;
    public Double  totalDistanceM;
    public Integer anomalyCount;
    public String  riskLevel;

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    public static FlightSessionDto from(FlightSession s) {
        FlightSessionDto dto = new FlightSessionDto();
        dto.id              = s.getId();
        dto.filename        = s.getFilename();
        dto.uploadedAt      = FMT.format(s.getUploadedAt());
        dto.droneType       = s.getDroneType();
        dto.healthy         = s.isHealthy();
        dto.durationSeconds = s.getDurationSeconds();
        dto.totalDistanceM  = s.getTotalDistanceM();
        dto.anomalyCount    = s.getAnomalyCount();
        dto.riskLevel       = s.getRiskLevel();
        return dto;
    }

    public static List<FlightSessionDto> fromList(List<FlightSession> sessions) {
        return sessions.stream().map(FlightSessionDto::from).collect(Collectors.toList());
    }
}
