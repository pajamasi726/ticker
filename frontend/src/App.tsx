import { useEffect, useState } from 'react'
import type { ServiceView } from './types'
import { fetchServices } from './api'
import { StatusWall } from './components/StatusWall'
import { ServiceDetailPanel } from './components/ServiceDetailPanel'
import { SummaryBar } from './components/SummaryBar'
import { LanguageSwitcher } from './components/LanguageSwitcher'
import { useT } from './i18n'

const POLL_MS = 5000

export default function App() {
  const [services, setServices] = useState<ServiceView[]>([])
  const [reachable, setReachable] = useState(true)
  const [lastOkAt, setLastOkAt] = useState<number | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const t = useT()

  useEffect(() => {
    let active = true
    const load = () =>
      fetchServices()
        .then((s) => {
          if (active) { setServices(s); setReachable(true); setLastOkAt(Date.now()) }
        })
        // Guardrail #1: a dead collector must be visible, not silently trusted as "all healthy".
        // Keep the last-known data so the operator sees what *was* true, but flag it unconfirmed.
        .catch(() => { if (active) setReachable(false) })
    load()
    const id = setInterval(load, POLL_MS)
    return () => { active = false; clearInterval(id) }
  }, [])

  // Tick once a second so the "stale Ns ago" banner counts up while unreachable (idle when healthy).
  useEffect(() => {
    if (reachable) return
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [reachable])

  const staleSeconds = lastOkAt != null ? Math.max(0, Math.round((now - lastOkAt) / 1000)) : null

  return (
    <>
      <div className="app__lang"><LanguageSwitcher /></div>
      <main className="app">
        {selectedId ? (
          <ServiceDetailPanel id={selectedId} onClose={() => setSelectedId(null)} />
        ) : (
          <>
            <header className="app__header">
              <h1>Ticker</h1>
              <span className="app__sub">{t('app.sub')}</span>
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
            ) : (
              <StatusWall services={services} onSelect={setSelectedId} stale={!reachable} />
            )}
          </>
        )}
      </main>
    </>
  )
}
