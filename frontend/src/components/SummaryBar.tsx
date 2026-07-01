import type { ServiceView, ServiceState } from '../types'
import { useT } from '../i18n'

const ORDER: ServiceState[] = ['UP', 'DEGRADED', 'DOWN', 'UNKNOWN']
const GLYPH: Record<ServiceState, string> = { UP: '●', DEGRADED: '◐', DOWN: '○', UNKNOWN: '?' }
const KEY: Record<ServiceState, string> = { UP: 'summary.up', DEGRADED: 'summary.degraded', DOWN: 'summary.down', UNKNOWN: 'summary.unknown' }

export function SummaryBar({ services }: { services: ServiceView[] }) {
  const t = useT()
  return (
    <div className="summary" role="status" aria-label="fleet summary">
      {ORDER.map((state) => {
        const n = services.filter((s) => s.state === state).length
        return (
          <span key={state} className={`summary__item state--${state.toLowerCase()}`}>
            <span aria-hidden>{GLYPH[state]}</span> {n} {t(KEY[state])}
          </span>
        )
      })}
    </div>
  )
}
