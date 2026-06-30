import { useEffect, useState } from 'react'
import { fetchDetail } from '../api'
import type { ServiceDetail } from '../types'

// Known metric → how to present it. Unknown metrics fall back to a raw row.
const LABELS: Record<string, (m: Record<string, number>) => string> = {
  'jvm.memory.used': (m) => `${((m.VALUE ?? 0) / 1_048_576).toFixed(0)} MB`,
  'jvm.memory.max': (m) => ((m.VALUE ?? 0) > 0 ? `${((m.VALUE ?? 0) / 1_048_576).toFixed(0)} MB` : '—'),
  'jvm.threads.live': (m) => `${m.VALUE}`,
  'process.cpu.usage': (m) => `${(m.VALUE * 100).toFixed(1)} %`,
  'process.uptime': (m) => `${Math.floor(m.VALUE / 60)} min`,
  'jvm.gc.pause': (m) => `${m.COUNT ?? 0} pauses / ${((m.TOTAL_TIME ?? 0) * 1000).toFixed(0)} ms`,
  'http.server.requests': (m) => `${m.COUNT ?? 0} req / ${((m.MAX ?? 0) * 1000).toFixed(0)} ms max`,
  'hikaricp.connections.active': (m) => `${m.VALUE} active`,
  'hikaricp.connections.max': (m) => `${m.VALUE} max`,
}
const TITLES: Record<string, string> = {
  'jvm.memory.used': 'Heap used',
  'jvm.memory.max': 'Heap max',
  'jvm.threads.live': 'Threads',
  'process.cpu.usage': 'CPU',
  'process.uptime': 'Uptime',
  'jvm.gc.pause': 'GC',
  'http.server.requests': 'HTTP',
  'hikaricp.connections.active': 'DB pool',
  'hikaricp.connections.max': 'DB pool max',
}

export function ServiceDetailPanel({ id, onClose }: { id: string; onClose: () => void }) {
  const [detail, setDetail] = useState<ServiceDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    const load = () =>
      fetchDetail(id)
        .then((d) => { if (active) { setDetail(d); setError(null) } })
        .catch((e) => { if (active) setError(String(e)) })
    load()
    const t = setInterval(load, 4000)
    return () => { active = false; clearInterval(t) }
  }, [id])

  return (
    <div className="detail-overlay" onClick={onClose}>
      <aside className="detail-panel" onClick={(e) => e.stopPropagation()}>
        <header className="detail-header">
          <h2 className="detail-title">{detail?.name ?? id}</h2>
          <span className={`state state--${(detail?.state ?? 'unknown').toLowerCase()}`}>
            {detail?.state ?? '…'}
          </span>
          <button className="detail-close" onClick={onClose} aria-label="Close">×</button>
        </header>
        {error && <p className="detail-error">{error}</p>}
        {detail?.type === 'HTTP' && (
          <p className="detail-note">HTTP target — no JVM metrics. Latency: {detail.latencyMs ?? '—'} ms</p>
        )}
        {detail && detail.type === 'SPRING' && detail.metrics.length === 0 && (
          <p className="detail-note">No metrics (target unreachable or actuator metrics not exposed).</p>
        )}
        <ul className="metric-cards">
          {detail?.metrics.map((m) => (
            <li key={`${m.name}|${m.tag ?? ''}`} className="metric-card">
              <span className="metric-title">{TITLES[m.name] ?? m.name}</span>
              <span className="metric-value">
                {(LABELS[m.name] ?? ((x: Record<string, number>) => JSON.stringify(x)))(m.measurements)}
              </span>
            </li>
          ))}
        </ul>
      </aside>
    </div>
  )
}
