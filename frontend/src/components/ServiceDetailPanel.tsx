import { useEffect, useRef, useState } from 'react'
import { fetchDetail, fetchAlertRules, updateAlertRule, fetchRecentAlerts } from '../api'
import type { ServiceDetail, AlertRule, AlertFire } from '../types'
import { MetricWidget } from './MetricWidget'
import { formatValue } from '../format'

const MAX_POINTS = 60
const POLL_MS = 5000

export function ServiceDetailPanel({ id, onClose }: { id: string; onClose: () => void }) {
  const [detail, setDetail] = useState<ServiceDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const series = useRef<Record<string, number[]>>({})
  const prevRaw = useRef<Record<string, number>>({})
  const [rules, setRules] = useState<Record<string, AlertRule>>({})
  const [recent, setRecent] = useState<AlertFire[]>([])

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
                if (w.perSecond) point = point / (POLL_MS / 1000) // normalize delta to a per-second rate
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

  // Full-page view (not an overlay), so Esc is the quick way back to the wall.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // Alert rules (fetched once) + recent fires (polled). Silently absent if alerting is disabled (404).
  useEffect(() => {
    let active = true
    fetchAlertRules()
      .then((rs) => { if (active) setRules(Object.fromEntries(rs.map((r) => [r.key, r]))) })
      .catch(() => { if (active) setRules({}) })
    const loadRecent = () => fetchRecentAlerts().then((a) => { if (active) setRecent(a) }).catch(() => {})
    loadRecent()
    const t = setInterval(loadRecent, POLL_MS)
    return () => { active = false; clearInterval(t) }
  }, [id])

  const onAlertSave = (key: string, patch: { enabled?: boolean; threshold?: number; cooldownSeconds?: number }) => {
    updateAlertRule(key, patch)
      .then((r) => setRules((prev) => ({ ...prev, [r.key]: r })))
      .catch(() => {})
  }

  const allWidgets = detail?.groups.flatMap((g) => g.widgets) ?? []
  const byKey = (k: string) => allWidgets.find((w) => w.key === k)
  const uptime = byKey('uptime')
  const uptimeText = uptime ? formatValue(uptime.value, 'SECONDS') : null
  const cpu = byKey('cpu-process')
  const heap = byKey('heap-used')
  const heapPct =
    heap && heap.value != null && heap.max != null && heap.max > 0
      ? Math.round((heap.value / heap.max) * 100)
      : null

  // Latest per-poll delta of a sibling widget (for client-side ratio widgets like error rate).
  const lastDelta = (k: string) => {
    const s = series.current[k]
    return s && s.length ? s[s.length - 1] : 0
  }
  const ratioValue = (r: { numerator: string[]; denominator: string[] }) => {
    const num = r.numerator.reduce((a, k) => a + lastDelta(k), 0)
    const den = r.denominator.reduce((a, k) => a + lastDelta(k), 0)
    return den > 0 ? num / den : 0
  }

  const timeAgo = (iso: string) => {
    const t = Date.parse(iso)
    if (Number.isNaN(t)) return ''
    const s = Math.max(0, Math.round((Date.now() - t) / 1000))
    return s < 60 ? `${s}s ago` : `${Math.round(s / 60)}m ago`
  }
  const myRecent = recent.filter((a) => a.targetId === id).slice(0, 5)

  return (
    <div className="detail-view">
      <header className="detail-header">
        <button className="detail-back" onClick={onClose} aria-label="Back to all services">← all services</button>
        <h2 className="detail-title">{detail?.name ?? id}</h2>
        <span className={`state state--${(detail?.state ?? 'unknown').toLowerCase()}`}>{detail?.state ?? '…'}</span>
        {detail?.type && <span className="detail-type">{detail.type}</span>}
        {uptimeText && <span className="detail-uptime">up {uptimeText}</span>}
        {cpu?.value != null && <span className="detail-stat">CPU {formatValue(cpu.value, 'PERCENT')}</span>}
        {heapPct != null && <span className="detail-stat">Heap {heapPct}%</span>}
      </header>
      {error && <p className="detail-error">{error}</p>}
      {myRecent.length > 0 && (
        <div className="recent-alerts" role="status">
          <span className="recent-alerts__title">⚠ Recent alerts</span>
          {myRecent.map((a, i) => (
            <span key={i} className="recent-alerts__item">
              {a.label} <b>{formatValue(a.value, a.unit)}</b> vs {formatValue(a.threshold, a.unit)} · {timeAgo(a.at)}
            </span>
          ))}
        </div>
      )}
      {detail?.type === 'HTTP' && (
        <p className="detail-note">HTTP target — no JVM metrics. Latency {detail.latencyMs ?? '—'} ms.</p>
      )}
      {detail?.type === 'SPRING' && detail.groups.length === 0 && (
        <p className="detail-note">No metrics — unreachable or actuator metrics not exposed.</p>
      )}
      {detail?.groups.map((group) => (
        <section key={group.title} className="detail-group">
          <h3 className="detail-group__title">{group.title}<span className="detail-group__count">{group.widgets.length}</span></h3>
          <div className="widget-grid">
            {group.widgets.map((w) => (
              <MetricWidget
                key={w.key}
                widget={w.ratio ? { ...w, value: ratioValue(w.ratio) } : w}
                series={series.current[w.key] ?? []}
                alertRule={rules[w.key] ?? null}
                onAlertSave={onAlertSave}
              />
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}
