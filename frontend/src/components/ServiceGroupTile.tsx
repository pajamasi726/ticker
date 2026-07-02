import type { ServiceView, ServiceState } from '../types'
import { useT } from '../i18n'

const GLYPH: Record<ServiceState, string> = {
  UP: '●',
  DEGRADED: '◐',
  DOWN: '○',
  UNKNOWN: '?',
}

interface Props {
  instances: ServiceView[] // ≥2 same-named replicas, pre-sorted worst-first
  onSelect?: (id: string) => void
  onRemove?: (id: string) => void
}

/**
 * One tile per application, N replicas listed inside. Real deployments (ASG / Beanstalk / k8s)
 * start N identical instances of one app at once — nobody names them individually — so the wall
 * shows the app ONCE and distinguishes replicas inside by their auto-assigned hostname:port + IP.
 * Clicking a row opens that instance's dashboard.
 */
export function ServiceGroupTile({ instances, onSelect, onRemove }: Props) {
  const t = useT()
  const name = instances[0].name
  const upCount = instances.filter((i) => i.state === 'UP').length
  const aggregate: ServiceState =
    upCount === instances.length ? 'UP'
      : instances.every((i) => i.state === 'DOWN') ? 'DOWN'
        : upCount > 0 || instances.some((i) => i.state === 'DEGRADED') ? 'DEGRADED'
          : 'UNKNOWN'
  const tags = [...new Set(instances.flatMap((i) => i.tags))]

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
      <div className="tile__rows">
        {instances.map((i) => (
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
      {tags.length > 0 && (
        <div className="tile__tags">
          {tags.map((tg) => <span key={tg} className="chip">{tg}</span>)}
        </div>
      )}
    </div>
  )
}
