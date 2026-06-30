import type { ServiceView } from '../types'
import { ServiceTile } from './ServiceTile'

interface Props {
  services: ServiceView[]
  onSelect?: (id: string) => void
}

export function StatusWall({ services, onSelect }: Props) {
  if (services.length === 0) {
    return (
      <p className="empty">
        No services yet — point an app's <code>ticker.client.collector-url</code> here.
      </p>
    )
  }
  return (
    <div className="wall">
      {services.map((s) => <ServiceTile key={s.id} service={s} onSelect={onSelect} />)}
    </div>
  )
}
