import type { ServiceView, ServiceState } from '../types'
import { Sparkline } from './Sparkline'

// State encoded by border + glyph + text label (not color alone — accessibility).
const GLYPH: Record<ServiceState, string> = {
  UP: '●',       // ●
  DEGRADED: '◐', // ◐
  DOWN: '○',     // ○
  UNKNOWN: '?',
}

export function ServiceTile({ service }: { service: ServiceView }) {
  return (
    <div className={`tile tile--${service.state.toLowerCase()}`}>
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
