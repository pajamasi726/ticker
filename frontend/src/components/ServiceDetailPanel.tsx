import { useEffect, useRef, useState } from 'react'
import { fetchDetail } from '../api'
import type { ServiceDetail } from '../types'
import { MetricWidget } from './MetricWidget'
import { formatValue } from '../format'

const MAX_POINTS = 60
const POLL_MS = 5000

export function ServiceDetailPanel({ id, onClose }: { id: string; onClose: () => void }) {
  const [detail, setDetail] = useState<ServiceDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const series = useRef<Record<string, number[]>>({})
  const prevRaw = useRef<Record<string, number>>({})

  useEffect(() => {
    let active = true
    series.current = {}
    prevRaw.current = {}
    const load = () =>
      fetchDetail(id)
        .then((d) => {
          if (!active) return
          for (const group of d.groups) {
            for (const w of group.widgets) {
              if (w.render !== 'CHART') continue
              const raw = w.value ?? 0
              let point = raw
              if (w.cumulative) {
                const prev = prevRaw.current[w.key]
                point = prev == null ? 0 : Math.max(0, raw - prev) // per-poll delta; clamp restarts
                prevRaw.current[w.key] = raw
              }
              const prevSeries = series.current[w.key] ?? []
              series.current[w.key] = [...prevSeries, point].slice(-MAX_POINTS)
            }
          }
          setDetail(d)
          setError(null)
        })
        .catch(() => {
          // Friendly, not a raw TypeError — the collector may be unreachable or the service deregistered.
          if (active) setError('Lost connection to the collector — details may be stale.')
        })
    load()
    const t = setInterval(load, POLL_MS)
    return () => {
      active = false
      clearInterval(t)
    }
  }, [id])

  const uptime = detail?.groups.flatMap((g) => g.widgets).find((w) => w.key === 'uptime')
  const uptimeText = uptime ? formatValue(uptime.value, 'SECONDS') : null

  return (
    <div className="detail-overlay" onClick={onClose}>
      <aside className="detail-panel" onClick={(e) => e.stopPropagation()}>
        <header className="detail-header">
          <h2 className="detail-title">{detail?.name ?? id}</h2>
          <span className={`state state--${(detail?.state ?? 'unknown').toLowerCase()}`}>{detail?.state ?? '…'}</span>
          {detail?.type && <span className="detail-type">{detail.type}</span>}
          {uptimeText && <span className="detail-uptime">up {uptimeText}</span>}
          <button className="detail-close" onClick={onClose} aria-label="Close">×</button>
        </header>
        {error && <p className="detail-error">{error}</p>}
        {detail?.type === 'HTTP' && (
          <p className="detail-note">HTTP target — no JVM metrics. Latency {detail.latencyMs ?? '—'} ms.</p>
        )}
        {detail?.type === 'SPRING' && detail.groups.length === 0 && (
          <p className="detail-note">No metrics — unreachable or actuator metrics not exposed.</p>
        )}
        {detail?.groups.map((group) => (
          <section key={group.title} className="detail-group">
            <h3 className="detail-group__title">{group.title}</h3>
            <div className="widget-grid">
              {group.widgets.map((w) => (
                <MetricWidget key={w.key} widget={w} series={series.current[w.key] ?? []} />
              ))}
            </div>
          </section>
        ))}
      </aside>
    </div>
  )
}
