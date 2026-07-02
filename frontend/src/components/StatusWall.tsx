import type { ServiceView, ServiceState } from '../types'
import { ServiceTile } from './ServiceTile'
import { ServiceGroupTile } from './ServiceGroupTile'
import { useT } from '../i18n'

const SEVERITY: Record<ServiceState, number> = { DOWN: 0, DEGRADED: 1, UNKNOWN: 2, UP: 3 }

export function StatusWall({ services, onSelect, onRemove, stale = false }: { services: ServiceView[]; onSelect?: (id: string) => void; onRemove?: (id: string) => void; stale?: boolean }) {
  const t = useT()
  if (services.length === 0) {
    return (<p className="empty">{t('wall.empty')}</p>)
  }
  // Group same-named instances: deployments start N replicas of one app at once, so the wall
  // shows each app once; replicas are distinguished inside by hostname:port / IP.
  const byName = new Map<string, ServiceView[]>()
  for (const s of services) {
    const list = byName.get(s.name)
    if (list) list.push(s)
    else byName.set(s.name, [s])
  }
  const worst = (list: ServiceView[]) => Math.min(...list.map((s) => SEVERITY[s.state]))
  const groups = [...byName.values()].map((list) =>
    [...list].sort((a, b) => SEVERITY[a.state] - SEVERITY[b.state] || (a.instance ?? '').localeCompare(b.instance ?? '')),
  ).sort((a, b) => worst(a) - worst(b) || a[0].name.localeCompare(b[0].name))
  return (
    <div className={`wall${stale ? ' wall--stale' : ''}`}>
      {groups.map((g) => g.length === 1
        ? <ServiceTile key={g[0].id} service={g[0]} onSelect={onSelect} onRemove={onRemove} />
        : <ServiceGroupTile key={g[0].name} instances={g} onSelect={onSelect} onRemove={onRemove} />)}
    </div>
  )
}
