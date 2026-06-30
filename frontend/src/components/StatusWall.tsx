import type { ServiceView, ServiceState } from '../types'
import { ServiceTile } from './ServiceTile'

const SEVERITY: Record<ServiceState, number> = { DOWN: 0, DEGRADED: 1, UNKNOWN: 2, UP: 3 }

export function StatusWall({ services, onSelect }: { services: ServiceView[]; onSelect?: (id: string) => void }) {
  if (services.length === 0) {
    return (<p className="empty">No services yet — point an app's <code>ticker.client.collector-url</code> here.</p>)
  }
  const sorted = [...services].sort((a, b) => SEVERITY[a.state] - SEVERITY[b.state] || a.name.localeCompare(b.name))
  return (<div className="wall">{sorted.map((s) => <ServiceTile key={s.id} service={s} onSelect={onSelect} />)}</div>)
}
