import { useEffect, useRef, useState } from 'react'
import type { GraphEdge, OutboundCall, TagStat } from '../types'
import { fetchGraph, fetchMetricBreakdown, fetchOutbound } from '../api'
import { formatValue } from '../format'
import { useT } from '../i18n'

const POLL_MS = 5000

const DOT: Record<string, string> = {
  UP: 'admin-dot--up', DEGRADED: 'admin-dot--degraded', DOWN: 'admin-dot--down', UNKNOWN: 'admin-dot--unknown',
}

/**
 * The no-tracing service map, one hop at a time: who THIS service calls (from Boot's
 * auto-instrumented http.client.requests), how often, how slowly — with a jump chip when the
 * called host is a target on our own wall. Aggregates, deliberately not per-request traces.
 */
export function OutboundCalls({ id, name, onSwitch }: { id: string; name?: string; onSwitch?: (id: string) => void }) {
  const t = useT()
  const [calls, setCalls] = useState<OutboundCall[] | null>(null)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [uris, setUris] = useState<Record<string, TagStat[]>>({})
  const prev = useRef<{ at: number; counts: Record<string, number> } | null>(null)
  const [rates, setRates] = useState<Record<string, number>>({})
  const [inbound, setInbound] = useState<GraphEdge[]>([])

  useEffect(() => {
    let active = true
    prev.current = null
    setCalls(null)
    setExpanded(null)
    setUris({})
    const load = () => fetchOutbound(id)
      .then((rows) => {
        if (!active) return
        setCalls(rows)
        const now = Date.now()
        const counts = Object.fromEntries(rows.map((r) => [r.host, r.count ?? 0]))
        const last = prev.current
        if (last && now > last.at) {
          const next: Record<string, number> = {}
          for (const r of rows) {
            const before = last.counts[r.host]
            if (before != null) next[r.host] = Math.max(0, ((r.count ?? 0) - before) / ((now - last.at) / 1000))
          }
          setRates(next)
        }
        prev.current = { at: now, counts }
      })
      .catch(() => { if (active) setCalls([]) })
    const loadInbound = () => {
      if (!name) return
      fetchGraph()
        .then((g) => { if (active) setInbound(g.edges.filter((e) => e.to === name)) })
        .catch(() => {})
    }
    load()
    loadInbound()
    const tmr = setInterval(() => { load(); loadInbound() }, POLL_MS)
    return () => { active = false; clearInterval(tmr) }
  }, [id, name])

  const toggle = (host: string) => {
    const next = expanded === host ? null : host
    setExpanded(next)
    if (next && !uris[next]) {
      fetchMetricBreakdown(id, 'http.client.requests', 'uri', `client.name:${next}`)
        .then((rows) => setUris((u) => ({ ...u, [next]: rows })))
        .catch(() => {})
    }
  }

  if (calls == null) return null // first load pending — keep the panel quiet
  const maxMean = Math.max(...calls.map((c) => c.mean ?? 0), 0)

  return (
    <section className="detail-group outbound">
      <h3 className="detail-group__title">
        {t('outbound.title')}
        <span className="detail-group__count">{calls.length}</span>
      </h3>
      {calls.length === 0 && inbound.length === 0 ? (
        <p className="outbound__hint">{t('outbound.empty')}</p>
      ) : calls.length === 0 ? null : (
        <div className="outbound__table" role="table">
          <div className="outbound__row outbound__row--head" role="row">
            <span>{t('outbound.target')}</span>
            <span className="outbound__num">{t('outbound.rps')}</span>
            <span className="outbound__num">{t('outbound.calls')}</span>
            <span className="outbound__lat">{t('outbound.avg')}</span>
            <span className="outbound__num">{t('outbound.max')}</span>
            <span className="outbound__num">5xx</span>
            <span />
          </div>
          {calls.map((c) => {
            const errPct = c.count && c.count > 0 ? ((c.error5xx ?? 0) / c.count) * 100 : 0
            return (
              <div key={c.host}>
                <div className="outbound__row" role="row">
                  <span className="outbound__host">
                    <span className="outbound__arrow" aria-hidden>→</span>
                    {c.targetId ? (
                      <button
                        className="outbound__link"
                        onClick={() => onSwitch?.(c.targetId as string)}
                        title={t('outbound.jump', { name: c.targetName ?? c.host })}
                      >
                        <span className={`admin-dot ${DOT[c.targetState ?? 'UNKNOWN']}`} aria-hidden>●</span>
                        {c.targetName}
                      </button>
                    ) : (
                      <span className="outbound__ext" title={t('outbound.external')}>{c.host}</span>
                    )}
                  </span>
                  <span className="outbound__num">{rates[c.host] != null ? `${rates[c.host].toFixed(1)}/s` : '—'}</span>
                  <span className="outbound__num">{c.count != null ? Math.round(c.count).toLocaleString() : '—'}</span>
                  <span className="outbound__lat">
                    <span className="outbound__bar" aria-hidden>
                      <span
                        className={`outbound__bar-fill${(c.mean ?? 0) >= maxMean && calls.length > 1 ? ' outbound__bar-fill--worst' : ''}`}
                        style={{ width: maxMean > 0 ? `${Math.max(4, ((c.mean ?? 0) / maxMean) * 100)}%` : '0%' }}
                      />
                    </span>
                    {formatValue(c.mean, 'SECONDS')}
                  </span>
                  <span className="outbound__num">{formatValue(c.max, 'SECONDS')}</span>
                  <span className={`outbound__num${errPct > 0 ? ' outbound__err' : ''}`}>
                    {c.count ? `${errPct.toFixed(errPct > 0 && errPct < 1 ? 1 : 0)}%` : '—'}
                  </span>
                  <button
                    className="outbound__chev"
                    onClick={() => toggle(c.host)}
                    aria-expanded={expanded === c.host}
                    aria-label={t('outbound.paths')}
                  >{expanded === c.host ? '▾' : '▸'}</button>
                </div>
                {expanded === c.host && (
                  <div className="outbound__uris">
                    {!uris[c.host] ? (
                      <p className="outbound__hint">…</p>
                    ) : uris[c.host].length === 0 ? (
                      <p className="outbound__hint">{t('outbound.noPaths')}</p>
                    ) : (
                      uris[c.host].slice(0, 8).map((u) => (
                        <div key={u.value} className="outbound__uri-row">
                          <span className="outbound__uri" title={u.value}>{u.value}</span>
                          <span className="outbound__num">{u.count != null ? Math.round(u.count).toLocaleString() : '—'}</span>
                          <span className="outbound__num">{formatValue(u.mean, 'SECONDS')}</span>
                          <span className="outbound__num">{formatValue(u.max, 'SECONDS')}</span>
                        </div>
                      ))
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
      {inbound.length > 0 && (
        <div className="outbound__inbound">
          <h4 className="outbound__sub">{t('outbound.inboundTitle', { n: inbound.length })}</h4>
          {inbound.map((e) => {
            const errPct = e.count > 0 ? (e.error5xx / e.count) * 100 : 0
            return (
              <div key={e.from} className="outbound__row outbound__row--in">
                <span className="outbound__host">
                  <span className="outbound__arrow" aria-hidden>←</span>
                  <span className="outbound__from">{e.from}</span>
                </span>
                <span className="outbound__num" />
                <span className="outbound__num">{Math.round(e.count).toLocaleString()}</span>
                <span className="outbound__lat">{formatValue(e.mean, 'SECONDS')}</span>
                <span className="outbound__num">{formatValue(e.max, 'SECONDS')}</span>
                <span className={`outbound__num${errPct > 0 ? ' outbound__err' : ''}`}>{e.count ? `${errPct.toFixed(errPct > 0 && errPct < 1 ? 1 : 0)}%` : '—'}</span>
                <span />
              </div>
            )
          })}
        </div>
      )}
    </section>
  )
}
