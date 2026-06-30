import type { AlertRule } from '../types'

interface Props {
  rule: AlertRule
  onOpen: (key: string) => void
}

/** Just the 🔔 trigger in a widget head; opens the right-side alert drawer (Grafana-style). */
export function AlertBell({ rule, onOpen }: Props) {
  return (
    <button
      type="button"
      className={`alert-bell${rule.enabled ? ' alert-bell--on' : ''}`}
      title={rule.enabled ? 'Edit alert' : 'Set alert'}
      onClick={(e) => { e.stopPropagation(); onOpen(rule.key) }}
      aria-label="Configure alert"
    >🔔</button>
  )
}
