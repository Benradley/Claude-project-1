import { useState } from 'react'
import { downloadCsv, downloadPdf } from '../api/flightApi'

/**
 * Action bar shown at the top of the dashboard.
 * Provides CSV and PDF download buttons, and a "New analysis" reset button.
 */
export default function ExportBar({ file, onReset }) {
  const [csvLoading, setCsvLoading] = useState(false)
  const [pdfLoading, setPdfLoading] = useState(false)
  const [error, setError] = useState(null)

  async function handleCsv() {
    setError(null)
    setCsvLoading(true)
    try { await downloadCsv(file) }
    catch (e) { setError(e.message) }
    finally { setCsvLoading(false) }
  }

  async function handlePdf() {
    setError(null)
    setPdfLoading(true)
    try { await downloadPdf(file) }
    catch (e) { setError(e.message) }
    finally { setPdfLoading(false) }
  }

  return (
    <div className="export-bar">
      <div className="export-file">
        <span className="export-icon">📄</span>
        <span className="export-name">{file?.name}</span>
      </div>

      <div className="export-actions">
        {error && <span className="export-error">{error}</span>}
        <button
          className="btn-export btn-csv"
          onClick={handleCsv}
          disabled={csvLoading || pdfLoading}
        >
          {csvLoading ? <span className="spinner" /> : '⬇'} CSV
        </button>
        <button
          className="btn-export btn-pdf"
          onClick={handlePdf}
          disabled={csvLoading || pdfLoading}
        >
          {pdfLoading ? <span className="spinner" /> : '⬇'} PDF
        </button>
        <button className="btn-reset" onClick={onReset}>
          ↩ New analysis
        </button>
      </div>
    </div>
  )
}
