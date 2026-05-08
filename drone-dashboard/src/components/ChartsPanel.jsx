import { useMemo } from 'react'
import {
  Chart as ChartJS,
  CategoryScale, LinearScale, PointElement, LineElement,
  Title, Tooltip, Legend, Filler,
} from 'chart.js'
import { Line } from 'react-chartjs-2'

ChartJS.register(
  CategoryScale, LinearScale, PointElement, LineElement,
  Title, Tooltip, Legend, Filler
)

const CHART_OPTIONS = (yLabel, yMin) => ({
  responsive: true,
  maintainAspectRatio: false,
  animation: { duration: 400 },
  plugins: {
    legend:  { position: 'top', labels: { color: '#cbd5e1', font: { size: 12 } } },
    tooltip: { mode: 'index', intersect: false },
  },
  scales: {
    x: {
      ticks: { color: '#94a3b8', maxTicksLimit: 10 },
      grid:  { color: 'rgba(255,255,255,0.06)' },
    },
    y: {
      title: { display: true, text: yLabel, color: '#94a3b8' },
      min: yMin,
      ticks: { color: '#94a3b8' },
      grid:  { color: 'rgba(255,255,255,0.06)' },
    },
  },
})

/**
 * Three stacked time-series charts:
 *  1. Altitude AGL + MSL
 *  2. Ground speed
 *  3. Battery % + voltage (dual axis via two Line datasets)
 */
export default function ChartsPanel({ telemetry, battery }) {
  // Downsample to at most 300 points so charts stay responsive
  const pts = useMemo(() => downsample(telemetry || [], 300), [telemetry])

  const labels = useMemo(() => {
    if (!pts.length) return []
    const t0 = pts[0].timestampMs
    return pts.map(p => {
      const s = Math.round((p.timestampMs - t0) / 1000)
      return `${s}s`
    })
  }, [pts])

  // ── 1. Altitude ────────────────────────────────────────────────────────────
  const altData = useMemo(() => ({
    labels,
    datasets: [
      {
        label: 'Altitude AGL (m)',
        data: pts.map(p => round2(p.altitudeAglM)),
        borderColor: '#60a5fa',
        backgroundColor: 'rgba(96,165,250,0.1)',
        fill: true,
        tension: 0.3,
        pointRadius: 0,
        borderWidth: 2,
      },
      {
        label: 'Altitude MSL (m)',
        data: pts.map(p => round2(p.altitudeMslM)),
        borderColor: '#a78bfa',
        backgroundColor: 'transparent',
        fill: false,
        tension: 0.3,
        pointRadius: 0,
        borderWidth: 1.5,
        borderDash: [4, 4],
      },
    ],
  }), [pts, labels])

  // ── 2. Ground speed ────────────────────────────────────────────────────────
  const speedData = useMemo(() => ({
    labels,
    datasets: [
      {
        label: 'Ground speed (m/s)',
        data: pts.map(p => round2(p.groundSpeedMs)),
        borderColor: '#34d399',
        backgroundColor: 'rgba(52,211,153,0.1)',
        fill: true,
        tension: 0.3,
        pointRadius: 0,
        borderWidth: 2,
      },
    ],
  }), [pts, labels])

  // ── 3. Battery ─────────────────────────────────────────────────────────────
  // Build regression overlay from BatteryModel coefficients
  const batteryData = useMemo(() => {
    const datasets = []

    // Actual battery %
    const hasPct = pts.some(p => p.batteryPct > 0)
    if (hasPct) {
      datasets.push({
        label: 'Battery % (actual)',
        data: pts.map(p => round2(p.batteryPct)),
        borderColor: '#fbbf24',
        backgroundColor: 'rgba(251,191,36,0.1)',
        fill: true,
        tension: 0.3,
        pointRadius: 0,
        borderWidth: 2,
      })
    }

    // Voltage
    const hasV = pts.some(p => p.batteryV > 0)
    if (hasV) {
      datasets.push({
        label: 'Voltage (V)',
        data: pts.map(p => round2(p.batteryV)),
        borderColor: '#f87171',
        backgroundColor: 'transparent',
        fill: false,
        tension: 0.3,
        pointRadius: 0,
        borderWidth: 2,
        borderDash: [5, 3],
      })
    }

    // Regression model overlay (if coefficients available)
    if (battery?.coefficients?.length && hasPct && pts.length) {
      const t0 = pts[0].timestampMs
      const coeffs = battery.coefficients // [c_n, ..., c_1, c_0] descending degree
      datasets.push({
        label: 'Battery model (fit)',
        data: pts.map(p => {
          const tMin = (p.timestampMs - t0) / 60000
          return round2(polyEval(coeffs, tMin))
        }),
        borderColor: '#e879f9',
        backgroundColor: 'transparent',
        fill: false,
        tension: 0.1,
        pointRadius: 0,
        borderWidth: 1.5,
        borderDash: [2, 2],
      })
    }

    return { labels, datasets }
  }, [pts, labels, battery])

  if (!pts.length) {
    return (
      <div className="card charts-empty">
        <div className="empty-state">No telemetry data available for charts.</div>
      </div>
    )
  }

  return (
    <div className="charts-panel">
      <div className="card chart-card">
        <div className="card-title">Altitude over Time</div>
        <div className="chart-wrap">
          <Line data={altData} options={CHART_OPTIONS('meters', 0)} />
        </div>
      </div>

      <div className="card chart-card">
        <div className="card-title">Ground Speed over Time</div>
        <div className="chart-wrap">
          <Line data={speedData} options={CHART_OPTIONS('m/s', 0)} />
        </div>
      </div>

      <div className="card chart-card">
        <div className="card-title">
          Battery over Time
          {battery?.rSquared > 0 && (
            <span className="card-title-sub"> — model R² = {battery.rSquared.toFixed(4)}</span>
          )}
        </div>
        <div className="chart-wrap">
          <Line data={batteryData} options={CHART_OPTIONS('% / V', undefined)} />
        </div>
      </div>
    </div>
  )
}

// ── helpers ───────────────────────────────────────────────────────────────────

function downsample(arr, maxPts) {
  if (arr.length <= maxPts) return arr
  const step = arr.length / maxPts
  return Array.from({ length: maxPts }, (_, i) => arr[Math.round(i * step)])
}

function round2(v) {
  return v != null ? Math.round(v * 100) / 100 : null
}

/** Evaluate polynomial with coefficients [c_n, ..., c_1, c_0] (descending degree). */
function polyEval(coeffs, x) {
  return coeffs.reduce((acc, c) => acc * x + c, 0)
}
