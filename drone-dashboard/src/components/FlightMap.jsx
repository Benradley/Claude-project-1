import { useMemo } from 'react'
import { MapContainer, TileLayer, Polyline, CircleMarker, Popup, useMap } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'

/**
 * Adjusts the map view to fit all telemetry points whenever points change.
 */
function FitBounds({ positions }) {
  const map = useMap()
  useMemo(() => {
    if (positions.length > 1) {
      map.fitBounds(positions, { padding: [40, 40] })
    }
  }, [positions, map]) // eslint-disable-line
  return null
}

/**
 * Leaflet map showing:
 * - Flight path polyline (gradient from takeoff=green to landing=red)
 * - Start marker (green)
 * - End marker (red)
 * - Anomaly pins (orange, with popup)
 */
export default function FlightMap({ telemetry, anomalies }) {
  // Filter to points with a real GPS fix
  const gpsPoints = useMemo(
    () => (telemetry || []).filter(p => p.hasGpsFix && p.latitude !== 0 && p.longitude !== 0),
    [telemetry]
  )

  const positions = useMemo(() => gpsPoints.map(p => [p.latitude, p.longitude]), [gpsPoints])

  // Color the path by altitude: low=blue, high=red
  const segments = useMemo(() => {
    if (positions.length < 2) return []
    const maxAlt = Math.max(...gpsPoints.map(p => p.altitudeAglM), 1)
    return gpsPoints.slice(0, -1).map((p, i) => {
      const ratio = Math.min(p.altitudeAglM / maxAlt, 1)
      const r = Math.round(30 + ratio * 200)
      const g = Math.round(144 - ratio * 100)
      const b = Math.round(255 - ratio * 200)
      return {
        positions: [[p.latitude, p.longitude], [gpsPoints[i + 1].latitude, gpsPoints[i + 1].longitude]],
        color: `rgb(${r},${g},${b})`,
      }
    })
  }, [gpsPoints, positions])

  // Center: midpoint of bounding box
  const center = useMemo(() => {
    if (!positions.length) return [37.7749, -122.4194]
    const lats = positions.map(p => p[0])
    const lons = positions.map(p => p[1])
    return [
      (Math.min(...lats) + Math.max(...lats)) / 2,
      (Math.min(...lons) + Math.max(...lons)) / 2,
    ]
  }, [positions])

  if (!gpsPoints.length) {
    return (
      <div className="card map-card map-empty">
        <div className="card-title">Flight Path</div>
        <div className="empty-state">No GPS data available for this flight.</div>
      </div>
    )
  }

  const start = positions[0]
  const end   = positions[positions.length - 1]

  return (
    <div className="card map-card">
      <div className="card-title">
        Flight Path
        <span className="card-title-sub"> — {gpsPoints.length} GPS points</span>
      </div>

      <div className="map-legend">
        <span className="legend-dot" style={{ background: '#1e90ff' }} /> Low altitude
        <span className="legend-dot" style={{ background: '#e84040', marginLeft: 12 }} /> High altitude
        <span className="legend-dot anomaly-dot" style={{ marginLeft: 12 }} /> Anomaly
      </div>

      <MapContainer
        center={center}
        zoom={15}
        className="leaflet-map"
        scrollWheelZoom={true}
      >
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://openstreetmap.org">OpenStreetMap</a>'
        />

        <FitBounds positions={positions} />

        {/* Altitude-colored path segments */}
        {segments.map((seg, i) => (
          <Polyline
            key={i}
            positions={seg.positions}
            color={seg.color}
            weight={3}
            opacity={0.85}
          />
        ))}

        {/* Start marker */}
        <CircleMarker center={start} radius={8} pathOptions={{ color: '#22c55e', fillColor: '#22c55e', fillOpacity: 1 }}>
          <Popup>Takeoff</Popup>
        </CircleMarker>

        {/* End marker */}
        <CircleMarker center={end} radius={8} pathOptions={{ color: '#ef4444', fillColor: '#ef4444', fillOpacity: 1 }}>
          <Popup>Landing</Popup>
        </CircleMarker>

        {/* Anomaly markers */}
        {(anomalies || []).map((a, i) =>
          a.latitude && a.longitude ? (
            <CircleMarker
              key={i}
              center={[a.latitude, a.longitude]}
              radius={9}
              pathOptions={{ color: '#f97316', fillColor: '#f97316', fillOpacity: 0.8 }}
            >
              <Popup>
                <strong>{a.type}</strong><br />
                {a.description}<br />
                <em>{a.severity}</em>
              </Popup>
            </CircleMarker>
          ) : null
        )}
      </MapContainer>
    </div>
  )
}
