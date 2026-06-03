/**
 * API layer for the Drone Analytics backend.
 * All calls go through Vite's /api proxy → http://localhost:8080.
 * JWT is read from localStorage and attached as Authorization: Bearer <token>.
 */

import { getToken } from './authApi'

const BASE = '/api'

function authHeaders() {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/**
 * Upload a flight log file and receive the full analysis report JSON.
 * The response includes a `telemetry` array of raw telemetry points
 * used to render the map and charts.
 *
 * @param {File} file - The log file selected by the user.
 * @param {function} onProgress - Optional callback: (percent: number) => void
 * @returns {Promise<object>} AnalysisReportDto
 */
export async function analyzeFile(file, onProgress) {
  const form = new FormData()
  form.append('file', file)

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${BASE}/flights/analyze`)
    const token = getToken()
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)

    if (onProgress) {
      xhr.upload.addEventListener('progress', (e) => {
        if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100))
      })
    }

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          resolve(JSON.parse(xhr.responseText))
        } catch {
          reject(new Error('Server returned invalid JSON'))
        }
      } else {
        reject(new Error(xhr.responseText || `Server error ${xhr.status}`))
      }
    }

    xhr.onerror = () => reject(new Error('Network error — is the backend running?'))
    xhr.send(form)
  })
}

/**
 * Download a CSV telemetry export for the uploaded file.
 * Triggers a browser file download.
 */
export async function downloadCsv(file) {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch(`${BASE}/flights/report/csv`, { method: 'POST', body: form, headers: authHeaders() })
  if (!res.ok) throw new Error(`CSV export failed: ${res.status}`)
  const blob = await res.blob()
  triggerDownload(blob, deriveFilename(file.name, '_report.csv'))
}

/**
 * Download a PDF report for the uploaded file.
 * Triggers a browser file download.
 */
export async function downloadPdf(file) {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch(`${BASE}/flights/report/pdf`, { method: 'POST', body: form, headers: authHeaders() })
  if (!res.ok) throw new Error(`PDF export failed: ${res.status}`)
  const blob = await res.blob()
  triggerDownload(blob, deriveFilename(file.name, '_report.pdf'))
}

/**
 * Runs the built-in DJI demo flight through the analytics pipeline.
 * No file upload required — the backend generates the telemetry directly.
 */
export async function fetchDemoReport() {
  const res = await fetch(`${BASE}/flights/demo`, {
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error(`Demo failed: ${res.status}`)
  return res.json()
}

/**
 * Fetches the paginated flight history for the authenticated user.
 */
export async function fetchHistory(page = 0, size = 20) {
  const res = await fetch(`${BASE}/flights?page=${page}&size=${size}`, {
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error(`History fetch failed: ${res.status}`)
  return res.json()
}

/**
 * Fetches the full AnalysisReportDto for a saved session.
 */
export async function fetchSessionReport(sessionId) {
  const res = await fetch(`${BASE}/flights/${sessionId}`, {
    headers: authHeaders(),
  })
  if (!res.ok) throw new Error(`Session ${sessionId} not found`)
  return res.json()
}

/**
 * Deletes a saved session.
 */
export async function deleteSession(sessionId) {
  const res = await fetch(`${BASE}/flights/${sessionId}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
  if (!res.ok && res.status !== 404) throw new Error(`Delete failed: ${res.status}`)
}

// ── helpers ───────────────────────────────────────────────────────────────────

function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

function deriveFilename(original, suffix) {
  const dot = original.lastIndexOf('.')
  const base = dot > 0 ? original.slice(0, dot) : original
  return base + suffix
}
