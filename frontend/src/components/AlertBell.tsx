import { useState } from 'react'
import type { AlertRule } from '../types'

interface Props {
  rule: AlertRule
  onSave: (key: string, patch: { enabled?: boolean; threshold?: number; cooldownSeconds?: number }) => void
}

/** A small per-metric alert control: 🔔 toggles a popover to set threshold / cooldown / enable. */
export function AlertBell({ rule, onSave }: Props) {
  const isPercent = rule.unit === 'PERCENT'
  const toDisplay = (t: number) => (isPercent ? Math.round(t * 100) : t)
  const fromDisplay = (d: number) => (isPercent ? d / 100 : d)

  const [open, setOpen] = useState(false)
  const [enabled, setEnabled] = useState(rule.enabled)
  const [threshold, setThreshold] = useState(toDisplay(rule.threshold))
  const [cooldown, setCooldown] = useState(rule.cooldownSeconds)

  const cmp = rule.comparator === 'GT' ? '>' : '<'

  const save = () => {
    onSave(rule.key, { enabled, threshold: fromDisplay(threshold), cooldownSeconds: cooldown })
    setOpen(false)
  }

  return (
    <span className="alert-bell-wrap">
      <button
        type="button"
        className={`alert-bell${rule.enabled ? ' alert-bell--on' : ''}`}
        title={`Alert when ${cmp} ${toDisplay(rule.threshold)}${isPercent ? '%' : ''}${rule.enabled ? '' : ' (off)'}`}
        onClick={(e) => { e.stopPropagation(); setOpen((o) => !o) }}
        aria-label="Configure alert"
      >🔔</button>
      {open && (
        <div className="alert-pop" onClick={(e) => e.stopPropagation()}>
          <label className="alert-pop__row alert-pop__toggle">
            <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} /> alert enabled
          </label>
          <div className="alert-pop__row">
            <span>when {cmp}</span>
            <input type="number" value={threshold} onChange={(e) => setThreshold(Number(e.target.value))} />
            <span>{isPercent ? '%' : ''}</span>
          </div>
          <div className="alert-pop__row">
            <span>cooldown</span>
            <input type="number" value={cooldown} onChange={(e) => setCooldown(Number(e.target.value))} />
            <span>s</span>
          </div>
          <div className="alert-pop__actions">
            <button type="button" onClick={() => setOpen(false)}>cancel</button>
            <button type="button" className="alert-pop__save" onClick={save}>save</button>
          </div>
        </div>
      )}
    </span>
  )
}
