import { useState, useRef } from 'react'

const ACCEPTED = '.bin,.txt,.ulg,.tlog,.json'
const FORMAT_LABELS = {
  txt:  'DJI',
  bin:  'ArduPilot',
  ulg:  'PX4 ULog',
  tlog: 'MAVLink TLOG',
  json: 'Parrot',
}

/**
 * Drag-and-drop / click-to-browse file upload panel.
 * Calls onAnalyze(file) when the user submits.
 */
export default function UploadPanel({ onAnalyze, loading, uploadProgress }) {
  const [file, setFile]       = useState(null)
  const [dragging, setDragging] = useState(false)
  const inputRef = useRef()

  const ext = file ? file.name.split('.').pop().toLowerCase() : null
  const label = ext ? (FORMAT_LABELS[ext] || ext.toUpperCase()) : null

  function handleFiles(files) {
    if (files && files[0]) setFile(files[0])
  }

  function onDrop(e) {
    e.preventDefault()
    setDragging(false)
    handleFiles(e.dataTransfer.files)
  }

  function submit(e) {
    e.preventDefault()
    if (file) onAnalyze(file)
  }

  return (
    <div className="upload-panel">
      <div className="upload-hero">
        <div className="upload-icon">🚁</div>
        <h1>Drone Flight Analytics</h1>
        <p className="upload-subtitle">
          Upload a flight log to get a full analytics report — path efficiency,
          battery model, anomaly detection, and airspace risk.
        </p>
      </div>

      <form onSubmit={submit}>
        {/* Drop zone */}
        <div
          className={`drop-zone ${dragging ? 'dragging' : ''} ${file ? 'has-file' : ''}`}
          onClick={() => !loading && inputRef.current.click()}
          onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
        >
          <input
            ref={inputRef}
            type="file"
            accept={ACCEPTED}
            style={{ display: 'none' }}
            onChange={(e) => handleFiles(e.target.files)}
          />
          {file ? (
            <div className="drop-file-info">
              <span className="drop-file-icon">📄</span>
              <div>
                <div className="drop-filename">{file.name}</div>
                <div className="drop-meta">
                  {label && <span className="badge badge-format">{label}</span>}
                  <span className="drop-size">{(file.size / 1024).toFixed(1)} KB</span>
                </div>
              </div>
              {!loading && (
                <button
                  type="button"
                  className="drop-clear"
                  onClick={(e) => { e.stopPropagation(); setFile(null) }}
                  title="Remove file"
                >×</button>
              )}
            </div>
          ) : (
            <div className="drop-empty">
              <span className="drop-arrow">⬆</span>
              <div>Drag & drop your log file here</div>
              <div className="drop-hint">or click to browse</div>
              <div className="drop-formats">
                Supported: DJI .txt · ArduPilot .bin · PX4 .ulg · MAVLink .tlog · Parrot .json
              </div>
            </div>
          )}
        </div>

        {/* Upload progress bar */}
        {loading && uploadProgress > 0 && uploadProgress < 100 && (
          <div className="progress-bar-wrap">
            <div className="progress-bar" style={{ width: `${uploadProgress}%` }} />
            <span className="progress-label">{uploadProgress}% uploaded</span>
          </div>
        )}

        {/* Analyze button */}
        <button
          type="submit"
          className={`btn-analyze ${loading ? 'loading' : ''}`}
          disabled={!file || loading}
        >
          {loading ? (
            <>
              <span className="spinner" /> Analyzing…
            </>
          ) : (
            'Analyze Flight'
          )}
        </button>
      </form>
    </div>
  )
}
