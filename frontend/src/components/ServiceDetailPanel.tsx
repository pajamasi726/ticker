import { useEffect, useRef, useState } from 'react'
import { fetchDetail } from '../api'
import type { ServiceDetail, MetricValue } from '../types'
import { LiveChart } from './LiveChart'

const MAX_POINTS = 60
// the chartable number per metric (gauges → VALUE; timers → MAX/COUNT)
function chartValue(m: MetricValue): number {
  return m.measurements.VALUE ?? m.measurements.MAX ?? m.measurements.COUNT ?? 0
}
const TITLE: Record<string, string> = {
  'jvm.memory.used': 'Heap used', 'jvm.memory.max': 'Heap max', 'jvm.threads.live': 'Threads',
  'process.cpu.usage': 'CPU', 'process.uptime': 'Uptime', 'jvm.gc.pause': 'GC',
  'http.server.requests': 'HTTP', 'hikaricp.connections.active': 'DB pool', 'hikaricp.connections.max': 'DB pool max',
}
function label(m: MetricValue): string {
  const v = m.measurements
  switch (m.name) {
    case 'jvm.memory.used': return `${((v.VALUE ?? 0) / 1_048_576).toFixed(0)} MB`
    case 'jvm.memory.max': return (v.VALUE ?? 0) > 0 ? `${((v.VALUE ?? 0) / 1_048_576).toFixed(0)} MB` : '—'
    case 'jvm.threads.live': return `${v.VALUE ?? 0}`
    case 'process.cpu.usage': return `${((v.VALUE ?? 0) * 100).toFixed(1)} %`
    case 'process.uptime': return `${Math.floor((v.VALUE ?? 0) / 60)} min`
    case 'jvm.gc.pause': return `${v.COUNT ?? 0} · ${((v.TOTAL_TIME ?? 0) * 1000).toFixed(0)} ms`
    case 'http.server.requests': return `${v.COUNT ?? 0} req · ${((v.MAX ?? 0) * 1000).toFixed(0)} ms`
    default: return `${v.VALUE ?? 0}`
  }
}

export function ServiceDetailPanel({ id, onClose }: { id: string; onClose: () => void }) {
  const [detail, setDetail] = useState<ServiceDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const series = useRef<Record<string, number[]>>({})

  useEffect(() => {
    let active = true
    series.current = {}
    const load = () =>
      fetchDetail(id)
        .then((d) => {
          if (!active) return
          for (const m of d.metrics) {
            const prev = series.current[m.name] ?? []
            series.current[m.name] = [...prev, chartValue(m)].slice(-MAX_POINTS)
          }
          setDetail(d); setError(null)
        })
        .catch((e) => { if (active) setError(String(e)) })
    load()
    const t = setInterval(load, 4000)
    return () => { active = false; clearInterval(t) }
  }, [id])

  const heapUsed = detail?.metrics.find((m) => m.name === 'jvm.memory.used')?.measurements.VALUE
  const heapMax = detail?.metrics.find((m) => m.name === 'jvm.memory.max')?.measurements.VALUE
  const heapPct = heapUsed != null && heapMax != null && heapMax > 0 ? (heapUsed / heapMax) * 100 : null

  return (
    <div className="detail-overlay" onClick={onClose}>
      <aside className="detail-panel" onClick={(e) => e.stopPropagation()}>
        <header className="detail-header">
          <h2 className="detail-title">{detail?.name ?? id}</h2>
          <span className={`state state--${(detail?.state ?? 'unknown').toLowerCase()}`}>{detail?.state ?? '…'}</span>
          <button className="detail-close" onClick={onClose} aria-label="Close">×</button>
        </header>
        {error && <p className="detail-error">{error}</p>}
        {detail?.type === 'HTTP' && <p className="detail-note">HTTP target — no JVM metrics. Latency {detail.latencyMs ?? '—'} ms.</p>}
        {detail?.type === 'SPRING' && detail.metrics.length === 0 && <p className="detail-note">No metrics — unreachable or actuator metrics not exposed.</p>}
        {heapPct != null && (
          <div className="gauge"><div className="gauge__label">Heap {heapPct.toFixed(0)}%</div><div className="gauge__track"><div className="gauge__fill" style={{ width: `${Math.min(heapPct, 100)}%` }} /></div></div>
        )}
        <div className="metric-cards">
          {detail?.metrics.map((m) => (
            <div key={`${m.name}|${m.tag ?? ''}`} className="metric-card">
              <span className="metric-title">{TITLE[m.name] ?? m.name}</span>
              <span className="metric-value">{label(m)}</span>
              <LiveChart data={series.current[m.name] ?? []} width={150} height={36} />
            </div>
          ))}
        </div>
      </aside>
    </div>
  )
}
