import type { ResolvedWidget, AlertRule } from './types'

export type Severity = 'none' | 'warn' | 'crit'

/** The quantity an alert threshold compares against: ratio (value/max) for gauges with a max, else raw. */
function quantity(w: ResolvedWidget): number | null {
  if (w.value == null) return null
  return w.max != null && w.max > 0 ? w.value / w.max : w.value
}

/**
 * Danger level driving a widget's colored bar. If an enabled alert rule exists, color by proximity to
 * its threshold: breaching → crit, within 85% of it → warn. Otherwise fall back to a gauge's value/max
 * (≥90% crit, ≥70% warn; higher-is-better inverts, e.g. disk free). 'none' when there's nothing to flag.
 */
export function severityOf(w: ResolvedWidget, rule: AlertRule | null | undefined): Severity {
  if (!w.available) return 'none'
  const q = quantity(w)
  if (rule && rule.enabled && q != null) {
    const t = rule.threshold
    const breach = rule.comparator === 'GT' ? q > t : q < t
    if (breach) return 'crit'
    const near = rule.comparator === 'GT' ? q >= t * 0.85 : q <= t * 1.15
    return near ? 'warn' : 'none'
  }
  if (w.value != null && w.max != null && w.max > 0) {
    const pct = Math.min((w.value / w.max) * 100, 100)
    const sev = w.higherIsBetter ? 100 - pct : pct
    if (sev >= 90) return 'crit'
    if (sev >= 70) return 'warn'
  }
  return 'none'
}
