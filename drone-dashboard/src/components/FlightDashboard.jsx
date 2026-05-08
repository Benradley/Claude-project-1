import SummaryCards  from './SummaryCards'
import FlightMap     from './FlightMap'
import ChartsPanel   from './ChartsPanel'
import AnomalyPanel  from './AnomalyPanel'
import ExportBar     from './ExportBar'

/**
 * Main dashboard — laid out after a successful analysis.
 * Receives the full AnalysisReportDto (including `telemetry` array) from the API.
 */
export default function FlightDashboard({ report, file, onReset }) {
  return (
    <div className="dashboard">
      <ExportBar file={file} onReset={onReset} />

      {/* ── Row 1: stat cards ─────────────────────────────────────────── */}
      <SummaryCards report={report} />

      {/* ── Row 2: map + anomalies side by side ───────────────────────── */}
      <div className="dashboard-row">
        <div className="dashboard-col col-map">
          <FlightMap
            telemetry={report.telemetry}
            anomalies={report.anomalies}
          />
        </div>
        <div className="dashboard-col col-anomaly">
          <AnomalyPanel
            anomalies={report.anomalies}
            airspaceRisk={report.airspaceRisk}
          />
        </div>
      </div>

      {/* ── Row 3: time-series charts ──────────────────────────────────── */}
      <ChartsPanel
        telemetry={report.telemetry}
        battery={report.battery}
      />
    </div>
  )
}
