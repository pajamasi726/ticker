import { useEffect, useMemo, useRef, useState } from 'react'
import type { ServiceGraph } from '../types'
import { fetchGraph } from '../api'
import { formatValue } from '../format'
import { useT } from '../i18n'

const POLL_MS = 10_000

const STATE_COLOR: Record<string, string> = {
  UP: '#2ecc71', DEGRADED: '#e0a106', DOWN: '#e5484d', UNKNOWN: '#5a6270',
}

interface Hover { x: number; y: number; from: string; to: string; rate: number | null; mean: number | null; max: number | null; errPct: number }

/**
 * The Zipkin-style dependency picture, minus Zipkin: nodes are wall services (state-colored),
 * arrows are aggregated call edges from /api/graph. Hand-rolled SVG on a circular layout —
 * built for the small-team case (a handful of services), no graph library.
 */
export function ServiceMap({ onSelect }: { onSelect: (name: string) => void }) {
  const t = useT()
  const [graph, setGraph] = useState<ServiceGraph | null>(null)
  const [hover, setHover] = useState<Hover | null>(null)
  const prev = useRef<{ at: number; counts: Record<string, number> } | null>(null)
  const [rates, setRates] = useState<Record<string, number>>({})

  useEffect(() => {
    let active = true
    const load = () => fetchGraph()
      .then((g) => {
        if (!active) return
        setGraph(g)
        const now = Date.now()
        const counts = Object.fromEntries(g.edges.map((e) => [`${e.from}→${e.to}`, e.count]))
        const last = prev.current
        if (last && now > last.at) {
          const next: Record<string, number> = {}
          for (const e of g.edges) {
            const k = `${e.from}→${e.to}`
            const before = last.counts[k]
            if (before != null) next[k] = Math.max(0, (e.count - before) / ((now - last.at) / 1000))
          }
          setRates(next)
        }
        prev.current = { at: now, counts }
      })
      .catch(() => {})
    load()
    const tmr = setInterval(load, POLL_MS)
    return () => { active = false; clearInterval(tmr) }
  }, [])

  const layout = useMemo(() => {
    if (!graph) return null
    const W = 1080
    const H = 560
    const cx = W / 2
    const cy = H / 2
    const services = graph.nodes.filter((n) => !n.external)
    const externals = graph.nodes.filter((n) => n.external)
    const pos = new Map<string, { x: number; y: number }>()
    const r = Math.min(W, H) / 2 - 90
    services.forEach((n, i) => {
      const a = (2 * Math.PI * i) / Math.max(services.length, 1) - Math.PI / 2
      pos.set(n.name, { x: cx + r * Math.cos(a), y: cy + r * Math.sin(a) })
    })
    // externals on a clearly-outer arc (right side), padded so they never sit on a service node
    externals.forEach((n, i) => {
      const a = -Math.PI * 0.12 + (i * Math.PI * 0.24) / Math.max(externals.length - 1, 1)
      pos.set(n.name, { x: cx + (r + 150) * Math.cos(a), y: cy + (r + 150) * Math.sin(a) })
    })
    return { W, H, pos }
  }, [graph])

  if (!graph || !layout) return <p className="map__hint">…</p>

  const maxMean = Math.max(...graph.edges.map((e) => e.mean ?? 0), 0)
  const maxRate = Math.max(...Object.values(rates), 0)

  return (
    <div className="map">
      {graph.edges.length === 0 && <p className="map__hint">{t('map.empty')}</p>}
      <svg viewBox={`0 0 ${layout.W} ${layout.H}`} className="map__svg" role="img" aria-label={t('map.title')}>
        <defs>
          {['#3d6fa8', '#e0a106', '#e5484d'].map((c) => (
            <marker key={c} id={`arr-${c.slice(1)}`} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
              <path d="M 0 1 L 9 5 L 0 9 z" fill={c} />
            </marker>
          ))}
        </defs>
        {graph.edges.map((e) => {
          const a = layout.pos.get(e.from)
          const b = layout.pos.get(e.to)
          if (!a || !b) return null
          const k = `${e.from}→${e.to}`
          const rate = rates[k] ?? null
          const errPct = e.count > 0 ? (e.error5xx / e.count) * 100 : 0
          const color = errPct > 0 ? '#e5484d' : (maxMean > 0 && (e.mean ?? 0) >= maxMean && graph.edges.length > 1 ? '#e0a106' : '#3d6fa8')
          const width = 1.5 + (maxRate > 0 && rate != null ? (rate / maxRate) * 3.5 : 1)
          // shorten the line so the arrowhead lands on the node ring, not its center
          const dx = b.x - a.x
          const dy = b.y - a.y
          const len = Math.hypot(dx, dy) || 1
          const pad = 26
          const x1 = a.x + (dx / len) * pad
          const y1 = a.y + (dy / len) * pad
          const x2 = b.x - (dx / len) * pad
          const y2 = b.y - (dy / len) * pad
          return (
            <line
              key={k}
              x1={x1} y1={y1} x2={x2} y2={y2}
              stroke={color} strokeWidth={width} markerEnd={`url(#arr-${color.slice(1)})`}
              opacity={0.85} className="map__edge"
              onMouseEnter={() => setHover({ x: (x1 + x2) / 2, y: (y1 + y2) / 2, from: e.from, to: e.to, rate, mean: e.mean, max: e.max, errPct })}
              onMouseLeave={() => setHover(null)}
            />
          )
        })}
        {graph.nodes.map((n) => {
          const p = layout.pos.get(n.name)
          if (!p) return null
          return (
            <g
              key={n.name}
              transform={`translate(${p.x},${p.y})`}
              className={n.external ? 'map__node map__node--ext' : 'map__node'}
              onClick={() => { if (!n.external) onSelect(n.name) }}
            >
              {n.external ? (
                <rect x={-9} y={-9} width={18} height={18} rx={4} fill="#12151c" stroke="#5a6270" strokeWidth={1.5} />
              ) : (
                <>
                  <circle r={14} fill="#12151c" stroke={STATE_COLOR[n.state]} strokeWidth={2.5} />
                  <circle r={4.5} fill={STATE_COLOR[n.state]} />
                </>
              )}
              <text y={n.external ? 26 : 32} textAnchor="middle" className="map__label">{n.name}</text>
            </g>
          )
        })}
        {hover && (
          <g transform={`translate(${Math.min(hover.x, layout.W - 190)},${Math.max(hover.y - 58, 8)})`} pointerEvents="none">
            <rect width={185} height={52} rx={8} fill="#0d0f14" stroke="#384057" />
            <text x={10} y={17} className="map__tip map__tip--title">{hover.from} → {hover.to}</text>
            <text x={10} y={33} className="map__tip">
              {hover.rate != null ? `${hover.rate.toFixed(1)}/s` : '—'} · avg {formatValue(hover.mean, 'SECONDS')} · max {formatValue(hover.max, 'SECONDS')}
            </text>
            <text x={10} y={46} className="map__tip">5xx {hover.errPct.toFixed(hover.errPct > 0 && hover.errPct < 1 ? 1 : 0)}%</text>
          </g>
        )}
      </svg>
      <p className="map__legend">
        <span><span className="map__dot" style={{ background: '#3d6fa8' }} />{t('map.legendOk')}</span>
        <span><span className="map__dot" style={{ background: '#e0a106' }} />{t('map.legendSlow')}</span>
        <span><span className="map__dot" style={{ background: '#e5484d' }} />{t('map.legend5xx')}</span>
        <span>{t('map.legendHint')}</span>
      </p>
    </div>
  )
}
