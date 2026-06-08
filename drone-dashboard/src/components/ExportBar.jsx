
/**
 * Action bar shown at the top of the dashboard.
 * Provides CSV and PDF download buttons, and a "New analysis" reset button.
 */
export default function ExportBar({ file, onReset }) {
  return (
    <div className="export-bar">
      <div className="export-file">
        <span className="export-icon">📄</span>
        <span className="export-name">{file?.name}</span>
      </div>

      <div className="export-actions">
        <button className="btn-reset" onClick={onReset}>
          ↩ New analysis
        </button>
      </div>
    </div>
  )
}
