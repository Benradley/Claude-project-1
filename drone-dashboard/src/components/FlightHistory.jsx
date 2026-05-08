import { useState, useEffect } from 'react'
import { fetchHistory, fetchSessionReport, deleteSession } from '../api/flightApi'

const RISK_COLOR = { Low: 'var(--green)', Moderate: 'var(--yellow)', High: 'var(--orange)', Critical: 'var(--red)' }

/**
 * Paginated list of the user's past flight sessions.
 * Clicking a session loads the full report back into the dashboard.
 */
export default function FlightHistory({ onLoadReport, onClose }) {
  const [data,    setData]    = useState(null)   // { sessions, totalCount, totalPages, currentPage }
  const [page,    setPage]    = useState(0)
  const [loading, setLoading] = useState(false)
  const [error,   setError]   = useState(null)
  const [deleting, setDeleting] = useState(null) // session id being deleted

  useEffect(() => { load(page) }, [page]) // eslint-disable-line

  async function load(p) {
    setLoading(true)
    setError(null)
    try {
      const result = await fetchHistory(p, 10)
      setData(result)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  async function handleLoad(session) {
    try {
      const report = await fetchSessionReport(session.id)
      onLoadReport(report, session.filename)
    } catch (e) {
      setError(e.message)
    }
  }

  async function handleDelete(e, sessionId) {
    e.stopPropagation()
    if (!window.confirm('Delete this flight record? This cannot be undone.')) return
    setDeleting(sessionId)
    try {
      await deleteSession(sessionId)
      load(page)
    } catch (e) {
      setError(e.message)
    } finally {
      setDeleting(null)
    }
  }

  return (
    <div className="history-panel">
      <div className="history-header">
        <div className="history-title">📋 Flight History</div>
        {data && (
          <div className="history-count">
            {data.totalCount} flight{data.totalCount !== 1 ? 's' : ''} saved
          </div>
        )}
        <button className="history-close" onClick={onClose} title="Close history">✕</button>
      </div>

      {error && <div className="history-error">{error}</div>}

      {loading && <div className="history-loading"><span className="spinner" /> Loading…</div>}

      {!loading && data?.sessions?.length === 0 && (
        <div className="history-empty">
          No saved flights yet. Analyse a log to get started.
        </div>
      )}

      <div className="history-list">
        {data?.sessions?.map(s => (
          <div
            key={s.id}
            className="history-item"
            onClick={() => handleLoad(s)}
            title="Click to reload this flight's report"
          >
            <div className="history-item-top">
              <span className="history-filename">{s.filename}</span>
              <span
                className="history-risk"
                style={{ color: RISK_COLOR[s.riskLevel] || 'var(--muted)' }}
              >
                {s.riskLevel || '—'}
              </span>
            </div>

            <div className="history-item-mid">
              <span className="history-date">{s.uploadedAt}</span>
              <span className="history-drone badge badge-format">{s.droneType || '?'}</span>
            </div>

            <div className="history-item-bot">
              <span>{s.durationSeconds != null ? s.durationSeconds.toFixed(0) + ' s' : '—'}</span>
              <span>{s.totalDistanceM != null ? Math.round(s.totalDistanceM) + ' m' : '—'}</span>
              <span>{s.anomalyCount ?? 0} anomal{s.anomalyCount === 1 ? 'y' : 'ies'}</span>
              <span
                className={`badge ${s.healthy ? 'badge-ok' : 'badge-warn'}`}
                style={{ fontSize: '0.68rem' }}
              >
                {s.healthy ? 'HEALTHY' : 'ISSUES'}
              </span>
            </div>

            <button
              className="history-delete"
              onClick={e => handleDelete(e, s.id)}
              disabled={deleting === s.id}
              title="Delete"
            >
              {deleting === s.id ? <span className="spinner" style={{ width: 12, height: 12 }} /> : '🗑'}
            </button>
          </div>
        ))}
      </div>

      {/* Pagination */}
      {data && data.totalPages > 1 && (
        <div className="history-pagination">
          <button
            disabled={page === 0}
            onClick={() => setPage(p => p - 1)}
          >← Prev</button>
          <span>{page + 1} / {data.totalPages}</span>
          <button
            disabled={page >= data.totalPages - 1}
            onClick={() => setPage(p => p + 1)}
          >Next →</button>
        </div>
      )}
    </div>
  )
}
