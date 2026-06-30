import type { ServiceView, ServiceState } from '../types'
import { Sparkline } from './Sparkline'

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
}

export function ServiceTile({ service, onSelect }: Props) {
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
      </div>
      <div className="tile__meta">
        <span className="tile__state">{service.state}</span>
        <span className="tile__latency">
          {service.latencyMs != null ? `${service.latencyMs}ms` : '—'}
        </span>
      </div>
      <Sparkline values={service.sparkline} />
    </div>
  )
}
