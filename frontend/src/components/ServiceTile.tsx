import type { ServiceView, ServiceState } from '../types'
import { Sparkline } from './Sparkline'
import { useT } from '../i18n'

// State encoded by border + glyph + text label (not color alone — accessibility).
const GLYPH: Record<ServiceState, string> = {
  UP: '●',       // ●
  DEGRADED: '◐', // ◐
  DOWN: '○',     // ○
  UNKNOWN: '?',
}

interface Props {
  service: ServiceView
  onSelect?: (id: string) => void
  onRemove?: (id: string) => void
}

export function ServiceTile({ service, onSelect, onRemove }: Props) {
  const t = useT()
  // Static targets come from targets.yml and can't be removed; only UI/registered ones show ×.
  const removable = service.source !== 'STATIC'
  return (
    <div
      className={`tile tile--${service.state.toLowerCase()}`}
      role="button"
      tabIndex={0}
      onClick={() => onSelect?.(service.id)}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') onSelect?.(service.id) }}
    >
      <div className="tile__head">
        <span className="tile__glyph" aria-hidden>{GLYPH[service.state]}</span>
        <span className="tile__name">{service.name}</span>
        {onRemove && removable && (
          <button
            type="button"
            className="tile__remove"
            aria-label={t('tile.remove')}
            title={t('tile.remove')}
            onClick={(e) => {
              e.stopPropagation()
              if (window.confirm(t('tile.removeConfirm', { name: service.name }))) onRemove(service.id)
            }}
          >×</button>
        )}
      </div>
      <div className="tile__meta">
        <span className="tile__state">{service.state}</span>
        <span className="tile__latency">
          {service.latencyMs != null ? `${service.latencyMs}ms` : '—'}
        </span>
      </div>
      {service.tags.length > 0 && (
        <div className="tile__tags">
          {service.tags.map((t) => <span key={t} className="chip">{t}</span>)}
        </div>
      )}
      <Sparkline values={service.sparkline} />
    </div>
  )
}
