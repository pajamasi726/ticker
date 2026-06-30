import type { Unit } from '../types'
import { formatValue } from '../format'

interface GaugeProps {
  label: string
  value: number | null
  max: number | null
  unit: Unit
  higherIsBetter?: boolean
}

export function Gauge({ label, value, max, unit, higherIsBetter = false }: GaugeProps) {
  const pct =
    value != null && max != null && max > 0 ? Math.min((value / max) * 100, 100) : null
  // Color by severity. For higher-is-better gauges (e.g. disk free) a full bar is healthy, so invert.
  const severity = pct == null ? null : higherIsBetter ? 100 - pct : pct
  const color =
    severity == null ? 'var(--text-faint)' : severity < 70 ? 'var(--up)' : severity < 90 ? 'var(--degraded)' : 'var(--down)'
  const maxText = unit !== 'PERCENT' && max != null && max > 0 ? ` / ${formatValue(max, unit)}` : ''
  return (
    <div className="widget gauge">
      <div className="widget__label">{label}</div>
      <div className="widget__value">
        {formatValue(value, unit)}
        <span className="gauge__max">{maxText}</span>
      </div>
      <div className="gauge__track">
        <div className="gauge__fill" style={{ width: `${pct ?? 0}%`, background: color }} />
      </div>
    </div>
  )
}
