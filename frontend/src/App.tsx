import { useCallback, useEffect, useState } from 'react'
import type { ServiceView } from './types'
import { fetchServices, removeTarget } from './api'
import { StatusWall } from './components/StatusWall'
import { ServiceDetailPanel } from './components/ServiceDetailPanel'
import { AdminPanel } from './components/AdminPanel'
import { ServiceMap } from './components/ServiceMap'
import { SummaryBar } from './components/SummaryBar'
import { AddMonitor } from './components/AddMonitor'
import { LanguageSwitcher } from './components/LanguageSwitcher'
import { useT } from './i18n'

const POLL_MS = 5000

export default function App() {
  const [services, setServices] = useState<ServiceView[]>([])
  const [reachable, setReachable] = useState(true)
  const [lastOkAt, setLastOkAt] = useState<number | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [adminOpen, setAdminOpen] = useState(false)
  const [wallMode, setWallMode] = useState<'tiles' | 'map'>('tiles')
  const t = useT()

  // Guardrail #1: a dead collector must be visible, not silently trusted as "all healthy".
  // On failure keep the last-known data so the operator sees what *was* true, but flag it unconfirmed.
  const reload = useCallback(() =>
    fetchServices()
      .then((s) => { setServices(s); setReachable(true); setLastOkAt(Date.now()) })
      .catch(() => { setReachable(false) }),
  [])

  useEffect(() => {
    reload()
    const id = setInterval(reload, POLL_MS)
    return () => clearInterval(id)
  }, [reload])

  // Tick once a second so the "stale Ns ago" banner counts up while unreachable (idle when healthy).
  useEffect(() => {
    if (reachable) return
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [reachable])

  const staleSeconds = lastOkAt != null ? Math.max(0, Math.round((now - lastOkAt) / 1000)) : null

  return (
    <>
      <div className="app__lang">
        <button
          className={`app__gear${adminOpen ? ' app__gear--active' : ''}`}
          onClick={() => setAdminOpen((v) => !v)}
          aria-label={t('admin.title')}
          title={t('admin.title')}
        >⚙</button>
        <LanguageSwitcher />
      </div>
      <main className="app">
        {adminOpen ? (
          <AdminPanel onClose={() => setAdminOpen(false)} />
        ) : selectedId ? (
          <ServiceDetailPanel
            id={selectedId}
            siblings={services.filter((s) => s.name === services.find((x) => x.id === selectedId)?.name)}
            onSwitch={setSelectedId}
            onClose={() => setSelectedId(null)}
          />
        ) : (
          <>
            <header className="app__header">
              <h1>Ticker</h1>
              <span className="app__sub">{t('app.sub')}</span>
              <div className="viewtoggle" role="tablist">
                <button className={wallMode === 'tiles' ? 'on' : ''} onClick={() => setWallMode('tiles')} role="tab" aria-selected={wallMode === 'tiles'}>{t('wall.tiles')}</button>
                <button className={wallMode === 'map' ? 'on' : ''} onClick={() => setWallMode('map')} role="tab" aria-selected={wallMode === 'map'}>{t('wall.map')}</button>
              </div>
              <SummaryBar services={services} />
            </header>
            {!reachable && (
              <div className="banner banner--unreachable" role="alert">
                <span className="banner__glyph" aria-hidden>⚠</span>
                <span>
                  <strong>{t('banner.title')}</strong>{' '}
                  {staleSeconds != null
                    ? t('banner.stale', { n: staleSeconds })
                    : t('banner.noUpdate')}
                </span>
              </div>
            )}
            {services.length === 0 && !reachable ? (
              <p className="empty">
                {t('banner.unreachableA')} <code>/api/services</code>{t('banner.unreachableB')}
              </p>
            ) : wallMode === 'map' ? (
              <ServiceMap onSelect={(name) => { const s = services.find((x) => x.name === name); if (s) setSelectedId(s.id) }} />
            ) : (
              <>
                <AddMonitor onAdded={reload} />
                <StatusWall
                  services={services}
                  onSelect={setSelectedId}
                  onRemove={(id) => { removeTarget(id).finally(reload) }}
                  stale={!reachable}
                />
              </>
            )}
          </>
        )}
      </main>
    </>
  )
}
