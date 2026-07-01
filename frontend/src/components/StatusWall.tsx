import type { ServiceView, ServiceState } from '../types'
import { ServiceTile } from './ServiceTile'
import { useT } from '../i18n'

const SEVERITY: Record<ServiceState, number> = { DOWN: 0, DEGRADED: 1, UNKNOWN: 2, UP: 3 }

export function StatusWall({ services, onSelect, onRemove, stale = false }: { services: ServiceView[]; onSelect?: (id: string) => void; onRemove?: (id: string) => void; stale?: boolean }) {
  const t = useT()
  if (services.length === 0) {
    return (<p className="empty">{t('wall.empty')}</p>)
  }
  const sorted = [...services].sort((a, b) => SEVERITY[a.state] - SEVERITY[b.state] || a.name.localeCompare(b.name))
  return (<div className={`wall${stale ? ' wall--stale' : ''}`}>{sorted.map((s) => <ServiceTile key={s.id} service={s} onSelect={onSelect} onRemove={onRemove} />)}</div>)
}
