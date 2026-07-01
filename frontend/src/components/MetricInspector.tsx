import { useEffect, useState } from 'react'
import type { ResolvedWidget, AlertRule, AlertFire, TagStat } from '../types'
import { formatValue } from '../format'
import { LiveChart } from './LiveChart'
import { fetchMetricBreakdown } from '../api'
import { useTimeFmt, formatTime } from '../timeFormat'
import { TimeFormatSelect } from './TimeFormatSelect'

// Widget keys that support a tag breakdown. All http.server.requests-based → by URI (endpoint).
// Error/success widgets scope the breakdown to that outcome, so you see WHICH endpoint erred.
const BREAKDOWN: Record<string, { metric: string; tag: string; filter?: string; title: string }> = {
  'http-requests': { metric: 'http.server.requests', tag: 'uri', title: 'Requests by endpoint' },
  rps: { metric: 'http.server.requests', tag: 'uri', title: 'Requests by endpoint' },
  'error-rate': { metric: 'http.server.requests', tag: 'uri', filter: 'outcome:CLIENT_ERROR', title: 'Client errors by endpoint' },
  'http-latency-avg': { metric: 'http.server.requests', tag: 'uri', title: 'Latency by endpoint' },
  'http-latency-max': { metric: 'http.server.requests', tag: 'uri', title: 'Latency by endpoint' },
  'http-success': { metric: 'http.server.requests', tag: 'uri', filter: 'outcome:SUCCESS', title: 'Successful requests by endpoint' },
  'http-client-error': { metric: 'http.server.requests', tag: 'uri', filter: 'outcome:CLIENT_ERROR', title: 'Client errors by endpoint' },
  'http-server-error': { metric: 'http.server.requests', tag: 'uri', filter: 'outcome:SERVER_ERROR', title: 'Server errors by endpoint' },
}

/** The quantity an alert compares: ratio (value/max) for gauges with a max, else the raw value. */
const quantity = (w: ResolvedWidget): number | null => {
  if (w.value == null) return null
  return w.max != null && w.max > 0 ? w.value / w.max : w.value
}

type SavePatch = { enabled?: boolean; threshold?: number; cooldownSeconds?: number; forSeconds?: number }

interface Props {
  serviceId: string
  serviceName: string
  widget: ResolvedWidget
  series: number[]
  rule: AlertRule | null
  recent: AlertFire[]
  onSaveAlert: (patch: SavePatch) => void
  onClose: () => void
}

/** Full-page per-metric detail: trend (auto/fixed scale + time axis) + min/avg/max, HTTP by-endpoint, alert. */
export function MetricInspector({ serviceId, serviceName, widget, series, rule, recent, onSaveAlert, onClose }: Props) {
  const bd = BREAKDOWN[widget.key] ?? null
  const [rows, setRows] = useState<TagStat[] | null>(null)
  const [fullScale, setFullScale] = useState(false)
  const fmt = useTimeFmt()

  useEffect(() => {
    if (!bd) return
    let active = true
    fetchMetricBreakdown(serviceId, bd.metric, bd.tag, bd.filter)
      .then((r) => { if (active) setRows(r) })
      .catch(() => { if (active) setRows([]) })
    return () => { active = false }
  }, [serviceId, bd])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // Rate widgets (perSecond) show the current rate (last series point), not the raw cumulative total.
  const currentValue = widget.perSecond ? (series.length ? series[series.length - 1] : null) : widget.value
  const nums = series.filter((n) => Number.isFinite(n))
  const stats = nums.length
    ? { min: Math.min(...nums), max: Math.max(...nums), avg: nums.reduce((a, b) => a + b, 0) / nums.length }
    : null

  return (
    <div className="metric-view">
      <header className="detail-header">
        <button className="detail-back" onClick={onClose} aria-label="Back to dashboard">← {serviceName}</button>
        <h2 className="detail-title">{widget.label}</h2>
        <span className="mi__current">
          {formatValue(currentValue, widget.unit)}{widget.perSecond && currentValue != null ? '/s' : ''}
        </span>
      </header>

      <div className="metric-view__cols">
        <div className="metric-view__main">
          {series.length > 1 ? (
            <section className="metric-panel">
              <div className="alert-drawer__label mi__trendhead">
                <span>Trend · last {series.length} samples{widget.cumulative && !widget.perSecond ? ' (per-poll delta)' : ''}</span>
                <span className="mi__scaletoggle">
                  <button className={!fullScale ? 'on' : ''} onClick={() => setFullScale(false)}>auto</button>
                  <button className={fullScale ? 'on' : ''} onClick={() => setFullScale(true)}>{widget.unit === 'PERCENT' ? '0–100%' : '0–max'}</button>
                </span>
              </div>
              <div className="mi__chart"><LiveChart data={series} unit={widget.unit} height={260} showTime fullScale={fullScale} intervalSec={5} timeFmt={fmt} /></div>
              {stats && (
                <div className="mi__stats">
                  <span>min <b>{formatValue(stats.min, widget.unit)}</b></span>
                  <span>avg <b>{formatValue(stats.avg, widget.unit)}</b></span>
                  <span>max <b>{formatValue(stats.max, widget.unit)}</b></span>
                </div>
              )}
            </section>
          ) : (
            <section className="metric-panel"><div className="mi__muted">Collecting samples… the trend appears after a few polls.</div></section>
          )}

          {bd && (
            <section className="metric-panel">
              <div className="alert-drawer__label">{bd.title} · totals since start</div>
              {rows == null ? (
                <div className="mi__muted">loading…</div>
              ) : rows.length === 0 ? (
                <div className="mi__muted">no data</div>
              ) : (
                <table className="mi__table">
                  <thead><tr><th>endpoint</th><th>count</th><th>avg</th><th>max</th></tr></thead>
                  <tbody>
                    {rows.map((r) => (
                      <tr key={r.value}>
                        <td className="mi__uri" title={r.value}>{r.value}</td>
                        <td>{r.count != null ? Math.round(r.count).toLocaleString() : '—'}</td>
                        <td>{r.mean != null ? formatValue(r.mean, 'SECONDS') : '—'}</td>
                        <td>{r.max != null ? formatValue(r.max, 'SECONDS') : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>
          )}
        </div>

        <div className="metric-view__side">
          {rule ? (
            <AlertSection rule={rule} current={quantity(widget)} recent={recent} onSave={onSaveAlert} />
          ) : (
            <section className="metric-panel"><div className="alert-drawer__label">🔔 Alert</div><div className="mi__muted">No alert available for this metric.</div></section>
          )}
        </div>
      </div>
    </div>
  )
}

/** The alert-rule editor: condition + live status + threshold slider + "for" duration + cooldown. */
function AlertSection({ rule, current, recent, onSave }: { rule: AlertRule; current: number | null; recent: AlertFire[]; onSave: (patch: SavePatch) => void }) {
  const isPercent = rule.unit === 'PERCENT'
  const toDisplay = (t: number) => (isPercent ? Math.round(t * 100) : t)
  const fromDisplay = (d: number) => (isPercent ? d / 100 : d)

  const [enabled, setEnabled] = useState(rule.enabled)
  const [threshold, setThreshold] = useState(toDisplay(rule.threshold))
  const [forSecs, setForSecs] = useState(rule.forSeconds)
  const [cooldown, setCooldown] = useState(rule.cooldownSeconds)
  const [saved, setSaved] = useState(false)
  const fmt = useTimeFmt()

  const cmp = rule.comparator === 'GT' ? '>' : '<'
  const thr = fromDisplay(threshold)
  const breaching = current != null && (rule.comparator === 'GT' ? current > thr : current < thr)
  const save = () => {
    onSave({ enabled, threshold: fromDisplay(threshold), cooldownSeconds: cooldown, forSeconds: forSecs })
    setSaved(true)
    setTimeout(() => setSaved(false), 1500)
  }

  return (
    <section className="metric-panel mi__alert">
      <div className="alert-drawer__label">🔔 Alert</div>
      <div className="alert-cond">
        <span className="alert-cond__metric">{rule.label}</span>
        <span className="alert-cond__op">{cmp}</span>
        <span className="alert-cond__th">{threshold}{isPercent ? '%' : ''}</span>
        {forSecs > 0 && <span className="alert-cond__for">for {forSecs}s</span>}
      </div>
      <div className={`alert-status alert-status--${!enabled ? 'off' : breaching ? 'bad' : 'ok'}`}>
        currently <b>{current != null ? formatValue(current, rule.unit) : '—'}</b>
        <span>· {!enabled ? 'disabled' : breaching ? 'BREACHING' : 'OK'}</span>
      </div>
      <label className="alert-field alert-field--toggle">
        <span>Enabled</span>
        <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
      </label>
      <div className="alert-field">
        <span className="alert-field__label">Threshold — alert when {cmp} {threshold}{isPercent ? '%' : ''}</span>
        <div className="alert-field__inline">
          {isPercent && <input type="range" min={0} max={100} value={threshold} onChange={(e) => setThreshold(Number(e.target.value))} />}
          <input type="number" value={threshold} onChange={(e) => setThreshold(Number(e.target.value))} />
          {isPercent && <span className="alert-field__unit">%</span>}
        </div>
      </div>
      <div className="alert-field">
        <span className="alert-field__label">For — must stay breaching this long before firing</span>
        <div className="alert-field__inline">
          <input type="number" value={forSecs} onChange={(e) => setForSecs(Number(e.target.value))} />
          <span className="alert-field__unit">seconds (0 = immediate)</span>
        </div>
      </div>
      <div className="alert-field">
        <span className="alert-field__label">Cooldown — wait between repeat alerts</span>
        <div className="alert-field__inline">
          <input type="number" value={cooldown} onChange={(e) => setCooldown(Number(e.target.value))} />
          <span className="alert-field__unit">seconds</span>
        </div>
      </div>
      {recent.length > 0 && (
        <div className="mi__fires">
          <div className="alert-drawer__label mi__fireshead"><span>Recent fires</span><TimeFormatSelect /></div>
          {recent.slice(0, 6).map((a, i) => (
            <div key={i} className="alert-drawer__fire"><b>{formatValue(a.value, a.unit)}</b> vs {formatValue(a.threshold, a.unit)} · {formatTime(a.at, fmt)}</div>
          ))}
        </div>
      )}
      <div className="alert-drawer__actions">
        <span className="alert-drawer__saved">{saved ? 'saved ✓' : ''}</span>
        <button className="alert-drawer__save" onClick={save}>Save</button>
      </div>
    </section>
  )
}
