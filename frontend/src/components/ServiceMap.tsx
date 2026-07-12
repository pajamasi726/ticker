import { useEffect, useMemo, useRef, useState } from 'react'
import type { GraphEdge, ServiceGraph } from '../types'
import { fetchGraph } from '../api'
import { formatValue } from '../format'
import { useT } from '../i18n'

const POLL_MS = 5_000
const NODE_W = 158
const NODE_H = 48
const ROW_GAP = 96
const BASE_GAP = 120 // min horizontal gap between columns
const LANE_STEP = 26 // spacing between vertical edge lanes inside a gap
const TRACK_STEP = 26 // spacing between highway tracks above the flow
const CORNER = 8 // rounded-corner radius on 90° bends

const STATE_COLOR: Record<string, string> = {
  UP: '#2ecc71', DEGRADED: '#e0a106', DOWN: '#e5484d', UNKNOWN: '#5a6270',
}

interface Pos { x: number; y: number }
interface Route { d: string; ax: number; ay: number; labelX: number; labelY: number }

/** Orthogonal path through the given corner points, with rounded 90° bends. */
function orthoPath(pts: Array<[number, number]>): string {
  if (pts.length < 2) return ''
  let d = `M ${pts[0][0]} ${pts[0][1]}`
  for (let i = 1; i < pts.length - 1; i++) {
    const [px, py] = pts[i - 1]
    const [cx, cy] = pts[i]
    const [nx, ny] = pts[i + 1]
    const r = Math.min(CORNER, Math.hypot(cx - px, cy - py) / 2, Math.hypot(nx - cx, ny - cy) / 2)
    d += ` L ${cx - Math.sign(cx - px) * r} ${cy - Math.sign(cy - py) * r}`
    d += ` Q ${cx} ${cy} ${cx + Math.sign(nx - cx) * r} ${cy + Math.sign(ny - cy) * r}`
  }
  d += ` L ${pts[pts.length - 1][0]} ${pts[pts.length - 1][1]}`
  return d
}

/**
 * Service map v3 — built to stay readable as the fleet grows. Layered left→right columns with
 * barycenter ordering (crossing reduction), gaps that WIDEN with edge traffic, and orthogonal
 * (90°-bend) edge routing: every adjacent-column edge gets its own vertical lane, and every
 * longer/backward edge rides a dedicated horizontal highway track above the flow — so lines
 * never overlap each other or stab through node cards. Ports fan out along each card's sides.
 * Edge labels stay always-on for small fleets and switch to hover/selection reveal beyond
 * 6 edges. Still zero graph-library dependencies.
 */
export function ServiceMap({ onSelect }: { onSelect: (name: string) => void }) {
  const t = useT()
  const [graph, setGraph] = useState<ServiceGraph | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  const [hovered, setHovered] = useState<string | null>(null)
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
        // The server caches the graph (~10s TTL): a poll can return the SAME snapshot as the last
        // one. A zero delta over that interval is aliasing, not silence — keep the previous rates
        // and keep prev anchored at the last DISTINCT snapshot so the next delta uses real elapsed time.
        if (last && Object.keys(counts).length === Object.keys(last.counts).length
            && Object.entries(counts).every(([k, v]) => last.counts[k] === v)) return
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
    const services = graph.nodes.filter((n) => !n.external).map((n) => n.name)
    const externals = graph.nodes.filter((n) => n.external).map((n) => n.name)
    const outEdges = new Map<string, string[]>()
    const inEdges = new Map<string, string[]>()
    for (const e of graph.edges) {
      outEdges.set(e.from, [...(outEdges.get(e.from) ?? []), e.to])
      inEdges.set(e.to, [...(inEdges.get(e.to) ?? []), e.from])
    }
    const connected = new Set<string>()
    for (const e of graph.edges) { connected.add(e.from); if (!e.external) connected.add(e.to) }

    // 1) Longest-path layering (Sugiyama-style): every caller ends up LEFT of every service it
    //    calls, so almost all edges run forward one or more columns and few need the highway.
    //    Bellman-Ford-ish passes with a depth cap keep call cycles from ping-ponging forever —
    //    whatever back-edge survives the cap simply rides the highway backward.
    const col = new Map<string, number>()
    services.filter((s) => connected.has(s)).forEach((s) => col.set(s, 0))
    const svcEdges = graph.edges.filter((e) => !e.external && col.has(e.from) && col.has(e.to) && e.from !== e.to)
    for (let pass = 0; pass < 8; pass++) {
      let changed = false
      for (const e of svcEdges) {
        const want = Math.min((col.get(e.from) ?? 0) + 1, 8)
        if ((col.get(e.to) ?? 0) < want) { col.set(e.to, want); changed = true }
      }
      if (!changed) break
    }
    const maxCol = Math.max(0, ...col.values())
    const extCol = maxCol + 1
    externals.forEach((x) => col.set(x, extCol))
    const idle = services.filter((s) => !connected.has(s))
    const C = extCol + (externals.length > 0 ? 1 : 0) // total columns

    const byCol = new Map<number, string[]>()
    for (const [name, c] of col) byCol.set(c, [...(byCol.get(c) ?? []), name])
    for (const names of byCol.values()) names.sort()

    // 2) Crossing reduction: a few barycenter sweeps — order each column by the mean row of its
    //    neighbors in the previous/next column, so edges run as horizontally as possible.
    const rowOf = new Map<string, number>()
    const reindex = () => { for (const names of byCol.values()) names.forEach((n, i) => rowOf.set(n, i)) }
    reindex()
    const bary = (name: string, neighbors: string[]) => {
      const rows = neighbors.map((n) => rowOf.get(n)).filter((r): r is number => r != null)
      return rows.length ? rows.reduce((a, b) => a + b, 0) / rows.length : (rowOf.get(name) ?? 0)
    }
    for (let sweep = 0; sweep < 2; sweep++) {
      for (let c = 1; c < C; c++) {
        const names = byCol.get(c)
        if (names) names.sort((a, b) => bary(a, inEdges.get(a) ?? []) - bary(b, inEdges.get(b) ?? []))
        reindex()
      }
      for (let c = C - 2; c >= 0; c--) {
        const names = byCol.get(c)
        if (names) names.sort((a, b) => bary(a, outEdges.get(a) ?? []) - bary(b, outEdges.get(b) ?? []))
        reindex()
      }
    }

    // 3) Classify edges: adjacent-column edges get a vertical LANE in their gap; anything longer
    //    (or backward / same-column) rides a horizontal highway TRACK above the flow.
    type Classified = { e: GraphEdge; kind: 'adj' | 'hwy'; upGap: number; downGap: number }
    const classified: Classified[] = []
    for (const e of graph.edges) {
      const cf = col.get(e.from)
      const ct = col.get(e.to)
      if (cf == null || ct == null) continue
      if (ct === cf + 1) classified.push({ e, kind: 'adj', upGap: cf, downGap: cf })
      else classified.push({ e, kind: 'hwy', upGap: cf, downGap: ct - 1 })
    }

    // Lane bookkeeping per gap (gap g sits between column g and g+1; g = -1 is the left margin,
    // used only by backward edges that re-enter column 0).
    const gapLanes = new Map<number, string[]>() // gap → edge keys, top-to-bottom by source row
    const laneIndex = new Map<string, number>() // `${key}@${gap}` → lane index
    const addLane = (g: number, key: string) => {
      gapLanes.set(g, [...(gapLanes.get(g) ?? []), key])
    }
    const srcRow = (e: GraphEdge) => rowOf.get(e.from) ?? 0
    for (const c of [...classified].sort((x, y) => srcRow(x.e) - srcRow(y.e))) {
      const key = `${c.e.from}→${c.e.to}`
      addLane(c.upGap, `${key}@up`)
      if (c.kind === 'hwy') addLane(c.downGap, `${key}@down`)
    }
    for (const [g, keys] of gapLanes) keys.forEach((k, i) => laneIndex.set(`${k}#${g}`, i))

    // 4) Geometry: gaps widen with their lane count; the left margin widens if backward lanes need it.
    const gapW = (g: number) => {
      const n = gapLanes.get(g)?.length ?? 0
      return Math.max(g === -1 ? 24 : BASE_GAP, (n + 1) * LANE_STEP)
    }
    const ML = gapW(-1) + 4
    const xLeft: number[] = [] // left edge of each column's cards
    for (let c = 0; c < Math.max(C, 1); c++) xLeft.push(c === 0 ? ML : xLeft[c - 1] + NODE_W + gapW(c - 1))
    const laneX = (g: number, key: string) => {
      const n = gapLanes.get(g)?.length ?? 1
      const k = laneIndex.get(`${key}#${g}`) ?? 0
      const start = g === -1 ? 0 : xLeft[g] + NODE_W
      const width = g === -1 ? ML - 4 : gapW(g)
      return start + ((k + 1) * width) / (n + 1)
    }

    // 5) Highway tracks: greedy interval packing so non-overlapping runs share a track.
    const hwys = classified.filter((c) => c.kind === 'hwy')
      .map((c) => {
        const key = `${c.e.from}→${c.e.to}`
        const xA = laneX(c.upGap, `${key}@up`)
        const xB = laneX(c.downGap, `${key}@down`)
        return { c, key, xStart: Math.min(xA, xB), xEnd: Math.max(xA, xB) }
      })
      .sort((a, b) => a.xStart - b.xStart)
    const trackEnds: number[] = []
    const trackOf = new Map<string, number>()
    for (const h of hwys) {
      let ti = trackEnds.findIndex((end) => end < h.xStart - 30)
      if (ti === -1) { ti = trackEnds.length; trackEnds.push(h.xEnd) } else trackEnds[ti] = h.xEnd
      trackOf.set(h.key, ti)
    }
    const tracks = trackEnds.length
    const topPad = 16 + (tracks > 0 ? tracks * TRACK_STEP + 20 : 0)
    const trackY = (ti: number) => 24 + ti * TRACK_STEP

    // 6) Node positions: columns vertically centered in the flow band; idle services wrap
    //    into rows in a bottom band instead of one unbounded strip.
    const tallest = Math.max(1, ...[...byCol.values()].map((v) => v.length))
    const flowH = Math.max(260, tallest * ROW_GAP)
    const W = Math.max(...xLeft.map((x) => x + NODE_W), ML + NODE_W) + 24
    const pos = new Map<string, Pos>()
    for (const [c, names] of byCol) {
      const totalH = names.length * ROW_GAP
      names.forEach((name, i) => {
        pos.set(name, { x: xLeft[c] + NODE_W / 2, y: topPad + (flowH - totalH) / 2 + i * ROW_GAP + ROW_GAP / 2 })
      })
    }
    const idlePerRow = Math.max(1, Math.floor((W - 48) / (NODE_W + 40)))
    idle.sort().forEach((name, i) => {
      pos.set(name, {
        x: 24 + NODE_W / 2 + (i % idlePerRow) * (NODE_W + 40),
        y: topPad + flowH + 30 + Math.floor(i / idlePerRow) * (NODE_H + 26) + NODE_H / 2,
      })
    })
    const idleRows = idle.length > 0 ? Math.ceil(idle.length / idlePerRow) : 0
    const H = topPad + flowH + (idleRows > 0 ? 30 + idleRows * (NODE_H + 26) : 20)

    // 7) Ports: fan incoming/outgoing edges along each card's left/right side (sorted by the
    //    other end's row) so several edges never converge on one point.
    const spread = (i: number, m: number) => (m <= 1 ? 0 : (i - (m - 1) / 2) * Math.min(14, (NODE_H - 20) / (m - 1)))
    const outPort = new Map<string, number>()
    const inPort = new Map<string, number>()
    const otherY = (name: string) => pos.get(name)?.y ?? 0
    for (const name of [...services, ...externals]) {
      const outs = classified.filter((c) => c.e.from === name).sort((a, b) => otherY(a.e.to) - otherY(b.e.to))
      outs.forEach((c, i) => outPort.set(`${c.e.from}→${c.e.to}`, spread(i, outs.length)))
      const ins = classified.filter((c) => c.e.to === name).sort((a, b) => otherY(a.e.from) - otherY(b.e.from))
      ins.forEach((c, i) => inPort.set(`${c.e.from}→${c.e.to}`, spread(i, ins.length)))
    }

    // 8) Routes: orthogonal points → rounded path + a label anchor on the longest horizontal run.
    const routes = new Map<string, Route>()
    for (const c of classified) {
      const key = `${c.e.from}→${c.e.to}`
      const a = pos.get(c.e.from)
      const b = pos.get(c.e.to)
      if (!a || !b) continue
      const y1 = a.y + (outPort.get(key) ?? 0)
      const y2 = b.y + (inPort.get(key) ?? 0)
      const x1 = a.x + NODE_W / 2
      const x2 = b.x - NODE_W / 2 - 8
      if (c.kind === 'adj') {
        const lx = laneX(c.upGap, `${key}@up`)
        const pts: Array<[number, number]> = Math.abs(y1 - y2) < 3
          ? [[x1, y1], [x2, y2]]
          : [[x1, y1], [lx, y1], [lx, y2], [x2, y2]]
        const entryLen = x2 - lx
        const label = Math.abs(y1 - y2) < 3
          ? { labelX: (x1 + x2) / 2, labelY: y1 - 12 }
          : entryLen >= 90
            ? { labelX: (lx + x2) / 2, labelY: y2 - 12 }
            : { labelX: lx, labelY: (y1 + y2) / 2 }
        routes.set(key, { d: orthoPath(pts), ax: x2, ay: y2, ...label })
      } else {
        const upX = laneX(c.upGap, `${key}@up`)
        const downX = laneX(c.downGap, `${key}@down`)
        const ty = trackY(trackOf.get(key) ?? 0)
        const pts: Array<[number, number]> = [[x1, y1], [upX, y1], [upX, ty], [downX, ty], [downX, y2], [x2, y2]]
        routes.set(key, { d: orthoPath(pts), ax: x2, ay: y2, labelX: (upX + downX) / 2, labelY: ty - 12 })
      }
    }

    return { W, H, pos, idle: new Set(idle), routes }
  }, [graph])

  if (!graph || !layout) return <p className="map__hint">…</p>

  const maxMean = Math.max(...graph.edges.map((e) => e.mean ?? 0), 0)
  const maxRate = Math.max(...Object.values(rates), 0)
  const nodeRate = (name: string, dir: 'in' | 'out') =>
    graph.edges.reduce((a, e) => a + ((dir === 'out' ? e.from : e.to) === name ? (rates[`${e.from}→${e.to}`] ?? 0) : 0), 0)

  const edgeColor = (e: GraphEdge) => {
    const errPct = e.count > 0 ? (e.error5xx / e.count) * 100 : 0
    if (errPct > 0) return '#e5484d'
    if (maxMean > 0 && (e.mean ?? 0) >= maxMean && graph.edges.length > 1) return '#e0a106'
    return '#4d82c4'
  }

  const dimmed = (name: string) =>
    selected != null && name !== selected && !graph.edges.some((e) =>
      (e.from === selected && e.to === name) || (e.from === name && e.to === selected))

  // Always-on labels stay cozy on small fleets; on bigger ones they'd collide, so they
  // reveal per node on hover/selection instead.
  const focus = hovered ?? selected
  const labelAlways = graph.edges.length <= 6
  const maxParticles = graph.edges.length > 12 ? 2 : 4

  const selNode = selected ? graph.nodes.find((n) => n.name === selected) : null
  const uses = selected ? graph.edges.filter((e) => e.from === selected) : []
  const usedBy = selected ? graph.edges.filter((e) => e.to === selected) : []

  return (
    <div className="map">
      {graph.edges.length === 0 && <p className="map__hint">{t('map.empty')}</p>}
      <div className="map__stage">
        <svg viewBox={`0 0 ${layout.W} ${layout.H}`} className="map__svg" style={{ minWidth: layout.W > 2600 ? `${Math.round(layout.W * 0.7)}px` : undefined }} role="img" aria-label={t('map.title')} onClick={() => setSelected(null)}>
          {graph.edges.map((e, i) => {
            const k = `${e.from}→${e.to}`
            const route = layout.routes.get(k)
            if (!route) return null
            const rate = rates[k] ?? null
            const color = edgeColor(e)
            const width = 1.6 + (maxRate > 0 && rate != null ? (rate / maxRate) * 3 : 0.8)
            const isDim = selected != null && e.from !== selected && e.to !== selected
            const showLabel = labelAlways || (focus != null && (e.from === focus || e.to === focus))
            const particles = rate != null && rate > 0 ? Math.min(maxParticles, 1 + Math.floor(rate / 2)) : 0
            const dur = rate != null && rate > 0 ? Math.max(0.9, 3.2 - rate * 0.35) : 3
            const label = `${rate != null ? rate.toFixed(1) : '–'}/s · ${formatValue(e.mean, 'SECONDS')}`
            return (
              <g key={k} opacity={isDim ? 0.15 : 1} className="map__edgeg">
                <path d={route.d} fill="none" stroke={color} strokeWidth={width + 5} opacity={0.1} strokeLinejoin="round" />
                <path id={`edge-${i}`} d={route.d} fill="none" stroke={color} strokeWidth={width} opacity={0.9}
                  strokeLinecap="round" strokeLinejoin="round" />
                <polygon
                  points={`${route.ax},${route.ay} ${route.ax - 9},${route.ay - 4.5} ${route.ax - 9},${route.ay + 4.5}`}
                  fill={color}
                />
                {Array.from({ length: particles }, (_, p) => (
                  <circle key={p} r={2.6} fill={color} opacity={0.95}>
                    <animateMotion dur={`${dur}s`} repeatCount="indefinite" begin={`${(p * dur) / particles}s`}>
                      <mpath href={`#edge-${i}`} />
                    </animateMotion>
                  </circle>
                ))}
                {showLabel && (
                  <g transform={`translate(${route.labelX},${route.labelY})`} pointerEvents="none">
                    <rect x={-46} y={-11} width={92} height={18} rx={9} fill="#0d0f14" stroke="#2a2f3a" opacity={0.95} />
                    <text textAnchor="middle" y={3.5} className="map__edge-label">{label}</text>
                  </g>
                )}
              </g>
            )
          })}
          {graph.nodes.map((n) => {
            const p = layout.pos.get(n.name)
            if (!p) return null
            const color = STATE_COLOR[n.state]
            const inR = nodeRate(n.name, 'in')
            const outR = nodeRate(n.name, 'out')
            const isSel = selected === n.name
            return (
              <g
                key={n.name}
                transform={`translate(${p.x - NODE_W / 2},${p.y - NODE_H / 2})`}
                className="map__card"
                opacity={dimmed(n.name) ? 0.25 : 1}
                onClick={(ev) => { ev.stopPropagation(); setSelected(isSel ? null : n.name) }}
                onMouseEnter={() => setHovered(n.name)}
                onMouseLeave={() => setHovered((h) => (h === n.name ? null : h))}
              >
                <rect
                  width={NODE_W} height={NODE_H} rx={11}
                  fill={n.external ? '#0d0f14' : '#141924'}
                  stroke={isSel ? '#6ea8e0' : n.external ? '#384057' : color}
                  strokeWidth={isSel ? 2.5 : 1.8}
                  strokeDasharray={n.external ? '4 3' : undefined}
                />
                {!n.external && <circle cx={16} cy={NODE_H / 2 - 8} r={4.5} fill={color} />}
                <text x={n.external ? 12 : 27} y={NODE_H / 2 - 4} className={n.external ? 'map__name map__name--ext' : 'map__name'}>
                  {n.name.length > 16 ? `${n.name.slice(0, 15)}…` : n.name}
                </text>
                <text x={n.external ? 12 : 27} y={NODE_H / 2 + 13} className="map__sub">
                  {n.external ? t('map.external') : `↓ ${inR.toFixed(1)}/s · ↑ ${outR.toFixed(1)}/s`}
                </text>
              </g>
            )
          })}
        </svg>

        {selected && selNode && (
          <aside className="map__panel" role="dialog" aria-label={selected}>
            <header className="map__panel-head">
              {!selNode.external && <span className="admin-dot" style={{ color: STATE_COLOR[selNode.state] }}>●</span>}
              <strong className="map__panel-title">{selected}</strong>
              <button className="map__panel-close" onClick={() => setSelected(null)} aria-label="close">×</button>
            </header>
            {selNode.external && <p className="admin-muted admin-small">{t('map.externalHint')}</p>}

            {uses.length > 0 && (
              <>
                <h4 className="map__panel-sub">{t('map.uses', { n: uses.length })}</h4>
                {uses.map((e) => <PanelRow key={e.to} name={e.to} edge={e} rate={rates[`${e.from}→${e.to}`]} onPick={(nm) => setSelected(nm)} />)}
              </>
            )}
            {usedBy.length > 0 && (
              <>
                <h4 className="map__panel-sub">{t('map.usedBy', { n: usedBy.length })}</h4>
                {usedBy.map((e) => <PanelRow key={e.from} name={e.from} edge={e} rate={rates[`${e.from}→${e.to}`]} onPick={(nm) => setSelected(nm)} />)}
              </>
            )}
            {uses.length === 0 && usedBy.length === 0 && <p className="admin-muted admin-small">{t('map.noEdges')}</p>}

            {!selNode.external && (
              <button className="admin-btn admin-btn--primary map__panel-open" onClick={() => onSelect(selected)}>
                {t('map.openDetail')}
              </button>
            )}
          </aside>
        )}
      </div>
      <p className="map__legend">
        <span><span className="map__dot" style={{ background: '#4d82c4' }} />{t('map.legendOk')}</span>
        <span><span className="map__dot" style={{ background: '#e0a106' }} />{t('map.legendSlow')}</span>
        <span><span className="map__dot" style={{ background: '#e5484d' }} />{t('map.legend5xx')}</span>
        <span>{t('map.legendHint')}</span>
      </p>
    </div>
  )
}

function PanelRow({ name, edge, rate, onPick }: { name: string; edge: GraphEdge; rate?: number; onPick: (name: string) => void }) {
  const errPct = edge.count > 0 ? (edge.error5xx / edge.count) * 100 : 0
  return (
    <button className="map__panel-row" onClick={() => onPick(name)}>
      <span className="map__panel-name">{name}</span>
      <span className="map__panel-stats">
        {rate != null ? `${rate.toFixed(1)}/s` : '—'} · {formatValue(edge.mean, 'SECONDS')}
        {errPct > 0 && <span className="outbound__err"> · 5xx {errPct.toFixed(1)}%</span>}
      </span>
    </button>
  )
}
