import type { ReactNode } from 'react'
import type { Unit } from '../types'
import type { Severity } from '../severity'
import { formatValue } from '../format'

interface GaugeProps {
  label: string
  value: number | null
  max: number | null
  unit: Unit
  higherIsBetter?: boolean
  controls?: ReactNode
  onClick?: () => void
  severity?: Severity
}

export function Gauge({ label, value, max, unit, higherIsBetter = false, controls, onClick, severity = 'none' }: GaugeProps) {
  const pct =
    value != null && max != null && max > 0 ? Math.min((value / max) * 100, 100) : null
  // Fill color by how full the gauge is. For higher-is-better gauges (e.g. disk free) a full bar is healthy, so invert.
  const fillLevel = pct == null ? null : higherIsBetter ? 100 - pct : pct
  const color =
    fillLevel == null ? 'var(--text-faint)' : fillLevel < 70 ? 'var(--up)' : fillLevel < 90 ? 'var(--degraded)' : 'var(--down)'
  const maxText = unit !== 'PERCENT' && max != null && max > 0 ? ` / ${formatValue(max, unit)}` : ''
  return (
    <div
      className={`widget gauge${onClick ? ' widget--clickable' : ''}${severity === 'crit' ? ' widget--crit' : severity === 'warn' ? ' widget--warn' : ''}`}
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      onKeyDown={onClick ? (e) => { if (e.key === 'Enter' || e.key === ' ') onClick() } : undefined}
    >
      <div className="widget__head">
        <span className="widget__label">{label}</span>
        <span className="widget__head-end">
          {pct != null && <span className="gauge__pct" style={{ color }}>{Math.round(pct)}%</span>}
          {controls}
        </span>
      </div>
      <div className="widget__value">
        {formatValue(value, unit)}
        {maxText && <span className="gauge__max">{maxText}</span>}
      </div>
      <div className="gauge__track">
        <div className="gauge__fill" style={{ width: `${pct ?? 0}%`, background: color }} />
      </div>
    </div>
  )
}
