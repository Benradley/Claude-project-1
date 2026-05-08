/**
 * Lists all detected anomalies. If none, shows a "clean flight" message.
 * Also shows a summary of the airspace risk result.
 */
export default function AnomalyPanel({ anomalies, airspaceRisk }) {
  const air = airspaceRisk || {}

  const severityColor = {
    CRITICAL: 'var(--red)',
    HIGH:     'var(--orange)',
    MEDIUM:   'var(--yellow)',
    LOW:      'var(--green)',
  }

  const riskColor = {
    Critical: 'var(--red)',
    High:     'var(--orange)',
    Moderate: 'var(--yellow)',
    Low:      'var(--green)',
  }

  return (
    <div className="anomaly-panel">
      {/* Anomalies */}
      <div className="card">
        <div className="card-title">
          Anomalies Detected
          {anomalies?.length > 0 && (
            <span className="badge badge-warn" style={{ marginLeft: 8 }}>{anomalies.length}</span>
          )}
        </div>

        {!anomalies?.length ? (
          <div className="empty-state clean">
            <span className="clean-icon">✅</span>
            Flight data is clean — no anomalies detected.
          </div>
        ) : (
          <div className="anomaly-list">
            {anomalies.map((a, i) => (
              <div key={i} className="anomaly-item">
                <div
                  className="anomaly-severity"
                  style={{ background: severityColor[a.severity] || '#888' }}
                >
                  {a.severity}
                </div>
                <div className="anomaly-body">
                  <div className="anomaly-type">{a.type?.replace(/_/g, ' ')}</div>
                  <div className="anomaly-desc">{a.description}</div>
                  <div className="anomaly-meta">
                    {a.latitude !== 0 && (
                      <span>📍 {a.latitude.toFixed(5)}, {a.longitude.toFixed(5)}</span>
                    )}
                    <span>measured: <strong>{a.measuredValue?.toFixed(2)}</strong></span>
                    <span>threshold: <strong>{a.threshold?.toFixed(2)}</strong></span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Airspace risk */}
      <div className="card">
        <div className="card-title">Airspace Risk</div>

        <div className="airspace-summary">
          <div className="airspace-score">
            <div
              className="risk-ring"
              style={{ '--risk-pct': `${air.overallRiskScore || 0}%`, '--risk-color': riskColor[air.riskLevel] || '#888' }}
            >
              <div className="risk-ring-inner">
                <div className="risk-score-val">{(air.overallRiskScore || 0).toFixed(0)}</div>
                <div className="risk-score-unit">/ 100</div>
              </div>
            </div>
            <div
              className="risk-level-label"
              style={{ color: riskColor[air.riskLevel] || '#888' }}
            >
              {air.riskLevel}
            </div>
          </div>

          <div className="airspace-detail">
            {air.insideRestrictedZone && (
              <div className="airspace-violation">
                ⚠️ INSIDE RESTRICTED ZONE — VIOLATION
              </div>
            )}
            <div className="airspace-nearest">
              Nearest zone: <strong>{air.nearestZoneName || 'None'}</strong>
              {!isNaN(air.minDistanceToRestrictionM) && air.minDistanceToRestrictionM > 0 && (
                <span> ({Math.round(air.minDistanceToRestrictionM)} m away)</span>
              )}
            </div>

            {air.proximityEvents?.length > 0 && (
              <div className="proximity-list">
                <div className="proximity-header">Proximity events ({air.proximityEvents.length})</div>
                {air.proximityEvents.map((e, i) => (
                  <div key={i} className="proximity-item">
                    <span className="proximity-zone">{e.zoneName}</span>
                    <span className="proximity-type">{e.zoneType}</span>
                    <span className="proximity-dist">{Math.round(e.distanceM)} m</span>
                    <span className="proximity-contrib">+{e.riskContribution.toFixed(1)} risk</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
