import { useState } from 'react'
import type { AlertRule, AlertFire } from '../types'
import { formatValue } from '../format'

interface Props {
  rule: AlertRule
  current: number | null // live quantity the rule compares (0-1 for PERCENT rules)
  recent: AlertFire[]
  onSave: (patch: { enabled?: boolean; threshold?: number; cooldownSeconds?: number }) => void
  onClose: () => void
}

const ago = (iso: string) => {
  const t = Date.parse(iso)
  if (Number.isNaN(t)) return ''
  const s = Math.max(0, Math.round((Date.now() - t) / 1000))
  return s < 60 ? `${s}s ago` : `${Math.round(s / 60)}m ago`
}

/** Grafana-style right-side drawer to configure one metric's threshold alert. */
export function AlertDrawer({ rule, current, recent, onSave, onClose }: Props) {
  const isPercent = rule.unit === 'PERCENT'
  const toDisplay = (t: number) => (isPercent ? Math.round(t * 100) : t)
  const fromDisplay = (d: number) => (isPercent ? d / 100 : d)

  const [enabled, setEnabled] = useState(rule.enabled)
  const [threshold, setThreshold] = useState(toDisplay(rule.threshold))
  const [cooldown, setCooldown] = useState(rule.cooldownSeconds)
  const [saved, setSaved] = useState(false)

  const cmp = rule.comparator === 'GT' ? '>' : '<'
  const thresholdRatio = fromDisplay(threshold)
  const breaching = current != null && (rule.comparator === 'GT' ? current > thresholdRatio : current < thresholdRatio)

  const save = () => {
    onSave({ enabled, threshold: fromDisplay(threshold), cooldownSeconds: cooldown })
    setSaved(true)
    setTimeout(() => setSaved(false), 1500)
  }

  return (
    <div className="alert-drawer-overlay" onClick={onClose}>
      <aside className="alert-drawer" onClick={(e) => e.stopPropagation()}>
        <header className="alert-drawer__header">
          <span aria-hidden>🔔</span>
          <h3 className="alert-drawer__title">{rule.label} — alert</h3>
          <button className="alert-drawer__close" onClick={onClose} aria-label="Close">×</button>
        </header>

        <section className="alert-drawer__section">
          <div className="alert-drawer__label">Condition</div>
          <div className="alert-cond">
            <span className="alert-cond__metric">{rule.label}</span>
            <span className="alert-cond__op">{cmp}</span>
            <span className="alert-cond__th">{toDisplay(rule.threshold)}{isPercent ? '%' : ''}</span>
          </div>
          <div className={`alert-status alert-status--${!enabled ? 'off' : breaching ? 'bad' : 'ok'}`}>
            currently <b>{current != null ? formatValue(current, rule.unit) : '—'}</b>
            <span>· {!enabled ? 'disabled' : breaching ? 'BREACHING' : 'OK'}</span>
          </div>
        </section>

        <section className="alert-drawer__section">
          <label className="alert-field alert-field--toggle">
            <span>Enabled</span>
            <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
          </label>

          <div className="alert-field">
            <span className="alert-field__label">Threshold — alert when {cmp} {threshold}{isPercent ? '%' : ''}</span>
            <div className="alert-field__inline">
              {isPercent && (
                <input type="range" min={0} max={100} value={threshold} onChange={(e) => setThreshold(Number(e.target.value))} />
              )}
              <input type="number" value={threshold} onChange={(e) => setThreshold(Number(e.target.value))} />
              {isPercent && <span className="alert-field__unit">%</span>}
            </div>
          </div>

          <div className="alert-field">
            <span className="alert-field__label">Cooldown — wait between alerts</span>
            <div className="alert-field__inline">
              <input type="number" value={cooldown} onChange={(e) => setCooldown(Number(e.target.value))} />
              <span className="alert-field__unit">seconds</span>
            </div>
          </div>
        </section>

        {recent.length > 0 && (
          <section className="alert-drawer__section">
            <div className="alert-drawer__label">Recent fires</div>
            {recent.slice(0, 6).map((a, i) => (
              <div key={i} className="alert-drawer__fire">
                <b>{formatValue(a.value, a.unit)}</b> vs {formatValue(a.threshold, a.unit)} · {ago(a.at)}
              </div>
            ))}
          </section>
        )}

        <div className="alert-drawer__actions">
          <span className="alert-drawer__saved">{saved ? 'saved ✓' : ''}</span>
          <button onClick={onClose}>Close</button>
          <button className="alert-drawer__save" onClick={save}>Save</button>
        </div>
      </aside>
    </div>
  )
}
