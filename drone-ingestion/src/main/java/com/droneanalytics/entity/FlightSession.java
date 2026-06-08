package com.droneanalytics.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity that records every analysed flight for a user.
 *
 * <p>The full {@code AnalysisReportDto} (including telemetry) is stored as a
 * JSON string so the dashboard can reconstruct the complete report from history
 * without re-uploading the original log.
 *
 * <p>Denormalised summary columns (droneType, healthy, durationSeconds, …) allow
 * fast paginated list queries without deserialising the JSON blob for every row.
 */
@Entity
@Table(name = "flight_sessions",
       indexes = @Index(name = "idx_fs_user_uploaded",
                        columnList = "user_id, uploaded_at DESC"))
public class FlightSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    /** Serialised AnalysisReportDto JSON (includes telemetry array). */
    @Column(name = "report_json", nullable = false, columnDefinition = "TEXT")
    private String reportJson;

    // ── Denormalised summary fields ──────────────────────────────────────────

    @Column(name = "drone_type", length = 50)
    private String droneType;

    @Column(nullable = false)
    private boolean healthy;

    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Column(name = "total_distance_m")
    private Double totalDistanceM;

    @Column(name = "anomaly_count")
    private Integer anomalyCount;

    @Column(name = "risk_level", length = 50)
    private String riskLevel;

    // ── Getters / setters ────────────────────────────────────────────────────

    public Long    getId()                       { return id; }
    public void    setId(Long id)                { this.id = id; }

    public User    getUser()                     { return user; }
    public void    setUser(User user)            { this.user = user; }

    public String  getFilename()                 { return filename; }
    public void    setFilename(String filename)  { this.filename = filename; }

    public Instant getUploadedAt()               { return uploadedAt; }
    public void    setUploadedAt(Instant t)      { this.uploadedAt = t; }

    public String  getReportJson()               { return reportJson; }
    public void    setReportJson(String json)    { this.reportJson = json; }

    public String  getDroneType()                { return droneType; }
    public void    setDroneType(String dt)       { this.droneType = dt; }

    public boolean isHealthy()                   { return healthy; }
    public void    setHealthy(boolean h)         { this.healthy = h; }

    public Double  getDurationSeconds()          { return durationSeconds; }
    public void    setDurationSeconds(Double d)  { this.durationSeconds = d; }

    public Double  getTotalDistanceM()           { return totalDistanceM; }
    public void    setTotalDistanceM(Double d)   { this.totalDistanceM = d; }

    public Integer getAnomalyCount()             { return anomalyCount; }
    public void    setAnomalyCount(Integer c)    { this.anomalyCount = c; }

    public String  getRiskLevel()                { return riskLevel; }
    public void    setRiskLevel(String rl)       { this.riskLevel = rl; }
}
