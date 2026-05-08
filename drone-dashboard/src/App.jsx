import { useState, useEffect } from 'react'
import { isLoggedIn, getUser, clearAuth } from './api/authApi'
import { analyzeFile } from './api/flightApi'
import AuthPage        from './components/AuthPage'
import UploadPanel     from './components/UploadPanel'
import FlightDashboard from './components/FlightDashboard'
import FlightHistory   from './components/FlightHistory'

/**
 * Root component — state machine:
 *
 *   guest     → AuthPage (login / register)
 *   upload    → UploadPanel (with history sidebar)
 *   loading   → UploadPanel with spinner
 *   dashboard → FlightDashboard (analysis result)
 *   error     → UploadPanel with error banner
 */
export default function App() {
  // Auth state
  const [authed,   setAuthed]   = useState(() => isLoggedIn())
  const [user,     setUser]     = useState(() => getUser())

  // Page state
  const [page,     setPage]     = useState('upload')  // 'upload' | 'loading' | 'dashboard' | 'error'
  const [report,   setReport]   = useState(null)
  const [file,     setFile]     = useState(null)
  const [error,    setError]    = useState(null)
  const [progress, setProgress] = useState(0)

  // History sidebar
  const [showHistory, setShowHistory] = useState(false)

  // Redirect to login if token expires mid-session
  useEffect(() => {
    if (!isLoggedIn()) { setAuthed(false); setUser(null) }
  }, [])

  // ── Auth callbacks ──────────────────────────────────────────────────────────
  function handleAuthSuccess(authResult) {
    setAuthed(true)
    setUser({ email: authResult.email, displayName: authResult.displayName })
    setPage('upload')
  }

  function handleLogout() {
    clearAuth()
    setAuthed(false)
    setUser(null)
    setPage('upload')
    setReport(null)
    setFile(null)
    setShowHistory(false)
  }

  // ── Flight analysis ─────────────────────────────────────────────────────────
  async function handleAnalyze(selectedFile) {
    setFile(selectedFile)
    setPage('loading')
    setError(null)
    setProgress(0)
    setShowHistory(false)
    try {
      const data = await analyzeFile(selectedFile, pct => setProgress(pct))
      setReport(data)
      setPage('dashboard')
    } catch (err) {
      // 401 = session expired
      if (err.message.includes('401') || err.message.toLowerCase().includes('unauthorized')) {
        clearAuth(); setAuthed(false); setUser(null); return
      }
      setError(err.message)
      setPage('error')
    }
  }

  function handleReset() {
    setPage('upload')
    setReport(null)
    setFile(null)
    setError(null)
    setProgress(0)
  }

  // Load a report from history (replaces current dashboard)
  function handleHistoryLoad(loadedReport, filename) {
    setReport(loadedReport)
    setFile({ name: filename })
    setPage('dashboard')
    setShowHistory(false)
  }

  // ── Not authenticated ───────────────────────────────────────────────────────
  if (!authed) {
    return <AuthPage onSuccess={handleAuthSuccess} />
  }

  // ── Dashboard (post-analysis) ───────────────────────────────────────────────
  if (page === 'dashboard' && report) {
    return (
      <div className="app-shell">
        <TopBar user={user} onLogout={handleLogout}
                onHistory={() => setShowHistory(v => !v)} showHistory={showHistory} />
        {showHistory && (
          <FlightHistory
            onLoadReport={handleHistoryLoad}
            onClose={() => setShowHistory(false)}
          />
        )}
        <FlightDashboard report={report} file={file} onReset={handleReset} />
      </div>
    )
  }

  // ── Upload page ─────────────────────────────────────────────────────────────
  return (
    <div className="app-shell">
      <TopBar user={user} onLogout={handleLogout}
              onHistory={() => setShowHistory(v => !v)} showHistory={showHistory} />

      <div className="upload-shell">
        {showHistory && (
          <div className="history-sidebar">
            <FlightHistory
              onLoadReport={handleHistoryLoad}
              onClose={() => setShowHistory(false)}
            />
          </div>
        )}
        <div className="upload-main">
          <UploadPanel
            onAnalyze={handleAnalyze}
            loading={page === 'loading'}
            uploadProgress={progress}
          />
          {page === 'error' && (
            <div className="error-banner">
              <strong>Analysis failed:</strong> {error}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// ── Top navigation bar ────────────────────────────────────────────────────────

function TopBar({ user, onLogout, onHistory, showHistory }) {
  return (
    <nav className="topbar">
      <div className="topbar-brand">🚁 Drone Analytics</div>
      <div className="topbar-actions">
        <button
          className={`topbar-btn ${showHistory ? 'active' : ''}`}
          onClick={onHistory}
          title="Flight history"
        >
          📋 History
        </button>
        <div className="topbar-user">
          <span className="topbar-name">{user?.displayName || user?.email}</span>
          <button className="topbar-logout" onClick={onLogout} title="Sign out">
            Sign out
          </button>
        </div>
      </div>
    </nav>
  )
}
