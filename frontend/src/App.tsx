import { useEffect, useState } from 'react'
import type { ServiceView } from './types'
import { fetchServices } from './api'
import { StatusWall } from './components/StatusWall'

const POLL_MS = 5000

export default function App() {
  const [services, setServices] = useState<ServiceView[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    const load = () =>
      fetchServices()
        .then((s) => { if (active) { setServices(s); setError(null) } })
        .catch((e) => { if (active) setError(String(e)) })
    load()
    const id = setInterval(load, POLL_MS)
    return () => { active = false; clearInterval(id) }
  }, [])

  return (
    <main className="app">
      <header className="app__header">
        <h1>Ticker</h1>
        <span className="app__sub">service liveness</span>
      </header>
      {error && <p className="error">{error}</p>}
      <StatusWall services={services} />
    </main>
  )
}
