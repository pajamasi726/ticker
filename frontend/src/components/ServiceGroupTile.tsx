import { useState } from 'react'
import type { ServiceView, ServiceState } from '../types'
import { useT } from '../i18n'

const GLYPH: Record<ServiceState, string> = {
  UP: '●',
  DEGRADED: '◐',
  DOWN: '○',
  UNKNOWN: '?',
}

/** Detailed rows shown before the tile collapses behind "+N more" — worst instances first. */
const MAX_ROWS = 3

interface Props {
  instances: ServiceView[] // ≥2 same-named replicas, pre-sorted worst-first
  onSelect?: (id: string) => void
  onRemove?: (id: string) => void
}

/**
 * One tile per application, N replicas inside. Real deployments (ASG / Beanstalk / k8s) start N
 * identical instances at once — nobody names them individually — so the wall shows the app ONCE
 * and distinguishes replicas by their auto identity (hostname:port + IP).
 *
 * Stays glanceable at any N: a dot strip shows EVERY instance's state, detailed rows show only
 * the worst MAX_ROWS (sick instances always surface), and "+N more" expands in place. Clicking a
 * row opens that instance's dashboard, where a switcher flips between replicas.
 */
export function ServiceGroupTile({ instances, onSelect, onRemove }: Props) {
  const t = useT()
  const [expanded, setExpanded] = useState(false)
  const name = instances[0].name
  const upCount = instances.filter((i) => i.state === 'UP').length
  const aggregate: ServiceState =
    upCount === instances.length ? 'UP'
      : instances.every((i) => i.state === 'DOWN') ? 'DOWN'
        : upCount > 0 || instances.some((i) => i.state === 'DEGRADED') ? 'DEGRADED'
          : 'UNKNOWN'
  const tags = [...new Set(instances.flatMap((i) => i.tags))]
  const visible = expanded ? instances : instances.slice(0, MAX_ROWS)
  const hiddenCount = instances.length - visible.length

  return (
    <div className={`tile tile--${aggregate.toLowerCase()} tile--group`}>
      <div className="tile__head">
        <span className="tile__glyph" aria-hidden>{GLYPH[aggregate]}</span>
        <span className="tile__name">{name}</span>
        <span className="tile__count">{t('tile.instances', { n: String(instances.length) })}</span>
      </div>
      <div className="tile__meta">
        <span className="tile__state">{upCount}/{instances.length} UP</span>
      </div>
      {instances.length > MAX_ROWS && (
        <div className="tile__dots">
          {instances.map((i) => (
            <span key={i.id} className={`dot dot--${i.state.toLowerCase()}`} title={`${i.instance ?? i.id} — ${i.state}`} />
          ))}
        </div>
      )}
      <div className="tile__rows">
        {visible.map((i) => (
          <div
            key={i.id}
            className={`irow irow--${i.state.toLowerCase()}`}
            role="button"
            tabIndex={0}
            onClick={() => onSelect?.(i.id)}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') onSelect?.(i.id) }}
          >
            <span className="irow__glyph" aria-hidden>{GLYPH[i.state]}</span>
            <span className="irow__host" title={`${i.instance ?? i.id}${i.ip ? ` · ${i.ip}` : ''}`}>
              {i.instance ?? i.id}
            </span>
            {i.ip && <span className="irow__ip">{i.ip}</span>}
            <span className="irow__lat">{i.latencyMs != null ? `${i.latencyMs}ms` : '—'}</span>
            {onRemove && i.source !== 'STATIC' && (
              <button
                type="button"
                className="irow__remove"
                aria-label={t('tile.remove')}
                title={t('tile.remove')}
                onClick={(e) => {
                  e.stopPropagation()
                  if (window.confirm(t('tile.removeConfirm', { name: `${name} (${i.instance ?? i.id})` }))) onRemove(i.id)
                }}
              >×</button>
            )}
          </div>
        ))}
      </div>
      {hiddenCount > 0 && (
        <button type="button" className="tile__morebtn" onClick={() => setExpanded(true)}>
          {t('tile.more', { n: String(hiddenCount) })}
        </button>
      )}
      {expanded && instances.length > MAX_ROWS && (
        <button type="button" className="tile__morebtn" onClick={() => setExpanded(false)}>
          {t('tile.less')}
        </button>
      )}
      {tags.length > 0 && (
        <div className="tile__tags">
          {tags.map((tg) => <span key={tg} className="chip">{tg}</span>)}
        </div>
      )}
    </div>
  )
}
