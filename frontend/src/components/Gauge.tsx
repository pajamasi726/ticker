import type { Unit } from '../types'
import { formatValue } from '../format'

interface GaugeProps {
  label: string
  value: number | null
  max: number | null
  unit: Unit
}

export function Gauge({ label, value, max, unit }: GaugeProps) {
  const pct =
    value != null && max != null && max > 0 ? Math.min((value / max) * 100, 100) : null
  const color =
    pct == null ? 'var(--text-faint)' : pct < 70 ? 'var(--up)' : pct < 90 ? 'var(--degraded)' : 'var(--down)'
  const maxText = max != null && max > 0 ? ` / ${formatValue(max, unit)}` : ''
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
