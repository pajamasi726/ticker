import type { ServiceView, ServiceState } from '../types'

const ORDER: ServiceState[] = ['UP', 'DEGRADED', 'DOWN', 'UNKNOWN']
const GLYPH: Record<ServiceState, string> = { UP: '●', DEGRADED: '◐', DOWN: '○', UNKNOWN: '?' }

export function SummaryBar({ services }: { services: ServiceView[] }) {
  return (
    <div className="summary" role="status" aria-label="fleet summary">
      {ORDER.map((state) => {
        const n = services.filter((s) => s.state === state).length
        return (
          <span key={state} className={`summary__item state--${state.toLowerCase()}`}>
            <span aria-hidden>{GLYPH[state]}</span> {n} {state.toLowerCase()}
          </span>
        )
      })}
    </div>
  )
}
