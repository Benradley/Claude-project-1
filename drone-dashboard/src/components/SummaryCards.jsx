/**
 * Grid of stat cards at the top of the dashboard.
 * Shows the most important numbers from the AnalysisReport at a glance.
 */
export default function SummaryCards({ report }) {
  const s   = report.summary         || {}
  const p   = report.pathOptimization || {}
  const b   = report.battery         || {}
  const air = report.airspaceRisk    || {}

  const riskColor = {
    Low:      'var(--green)',
    Moderate: 'var(--yellow)',
    High:     'var(--orange)',
    Critical: 'var(--red)',
  }

  const effColor = (score) => {
    if (score >= 70) return 'var(--green)'
    if (score >= 40) return 'var(--yellow)'
    return 'var(--orange)'
  }

  const healthBadge = report.healthy
    ? <span className="badge badge-ok">HEALTHY</span>
    : <span className="badge badge-warn">ISSUES DETECTED</span>

  return (
    <div className="summary-cards">
      {/* Overall health */}
      <div className="card card-wide">
        <div className="card-label">Flight status</div>
        <div className="card-value" style={{ fontSize: '1.1rem' }}>{healthBadge}</div>
        <div className="card-sub">{report.statusLine}</div>
      </div>

      {/* Duration */}
      <div className="card">
        <div className="card-label">Duration</div>
        <div className="card-value">{fmt1(s.durationSeconds)} s</div>
        <div className="card-sub">{fmt1(s.durationSeconds / 60)} min</div>
      </div>

      {/* Distance */}
      <div className="card">
        <div className="card-label">Total distance</div>
        <div className="card-value">{fmt0(s.totalDistanceM)} m</div>
        <div className="card-sub">straight-line {fmt0(s.straightLineDistanceM)} m</div>
      </div>

      {/* Max altitude */}
      <div className="card">
        <div className="card-label">Max altitude AGL</div>
        <div className="card-value">{fmt1(s.maxAltitudeAglM)} m</div>
        <div className="card-sub">avg speed {fmt1(s.avgGroundSpeedMs)} m/s</div>
      </div>

      {/* Max speed */}
      <div className="card">
        <div className="card-label">Max speed</div>
        <div className="card-value">{fmt1(s.maxGroundSpeedMs)} m/s</div>
        <div className="card-sub">{fmt1(s.maxGroundSpeedMs * 3.6)} km/h</div>
      </div>

      {/* Battery */}
      <div className="card">
        <div className="card-label">Battery drain</div>
        <div className="card-value">{fmt1(s.batteryDrainPct)} %</div>
        <div className="card-sub">{fmt2(b.avgDischargeRatePerMin)} %/min avg</div>
      </div>

      {/* Path efficiency */}
      <div className="card">
        <div className="card-label">Path efficiency</div>
        <div className="card-value" style={{ color: effColor(p.efficiencyScore) }}>
          {fmt1(p.efficiencyScore)} %
        </div>
        <div className="card-sub">{p.efficiencyRating}</div>
      </div>

      {/* Airspace risk */}
      <div className="card">
        <div className="card-label">Airspace risk</div>
        <div className="card-value" style={{ color: riskColor[air.riskLevel] || 'inherit' }}>
          {fmt1(air.overallRiskScore)} / 100
        </div>
        <div className="card-sub">{air.riskLevel} — {air.nearestZoneName}</div>
      </div>

      {/* Telemetry points */}
      <div className="card">
        <div className="card-label">Telemetry points</div>
        <div className="card-value">{s.totalPoints?.toLocaleString()}</div>
        <div className="card-sub">{fmt1(s.samplingRateHz)} Hz · {report.droneType}</div>
      </div>
    </div>
  )
}

const fmt0 = (v) => (v != null ? Math.round(v).toLocaleString() : '—')
const fmt1 = (v) => (v != null ? v.toFixed(1) : '—')
const fmt2 = (v) => (v != null ? v.toFixed(2) : '—')
