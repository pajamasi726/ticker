import { useEffect, useState } from 'react'
import type { ResolvedWidget, AlertRule, AlertFire, TagStat, MetricHistory } from '../types'
import { formatValue } from '../format'
import { LiveChart } from './LiveChart'
import { fetchMetricBreakdown, fetchMetricHistory } from '../api'
import { infoFor } from '../metricInfo'
import { useTimeFmt, formatTime } from '../timeFormat'
import { TimeFormatSelect } from './TimeFormatSelect'
import { useT } from '../i18n'

// Widget keys that support a tag breakdown. All http.server.requests-based → by URI (endpoint).
// Error/success widgets scope the breakdown to that outcome, so you see WHICH endpoint erred.
const BREAKDOWN: Record<string, { metric: string; tag: string; filter?: string; titleKey: string }> = {
  'http-requests': { metric: 'http.server.requests', tag: 'uri', titleKey: 'breakdown.requests' },
  rps:             { metric: 'http.server.requests', tag: 'uri', titleKey: 'breakdown.requests' },
  'error-rate':    { metric: 'http.server.requests', tag: 'uri', filter: 'outcome:CLIENT_ERROR', titleKey: 'breakdown.clientErrors' },
  'http-latency-avg': { metric: 'http.server.requests', tag: 'uri', titleKey: 'breakdown.latency' },
  'http-latency-max': { metric: 'http.server.requests', tag: 'uri', titleKey: 'breakdown.latency' },
  'http-success':      { metric: 'http.server.requests', tag: 'uri', filter: 'outcome:SUCCESS',       titleKey: 'breakdown.successRequests' },
  'http-client-error': { metric: 'http.server.requests', tag: 'uri', filter: 'outcome:CLIENT_ERROR',  titleKey: 'breakdown.clientErrors' },
  'http-server-error': { metric: 'http.server.requests', tag: 'uri', filter: 'outcome:SERVER_ERROR',  titleKey: 'breakdown.serverErrors' },
}

/** The quantity an alert compares: ratio (value/max) for gauges with a max, else the raw value. */
const quantity = (w: ResolvedWidget): number | null => {
  if (w.value == null) return null
  return w.max != null && w.max > 0 ? w.value / w.max : w.value
}

type HistRange = 'live' | '5m' | '15m' | '1h' | '6h' | '24h' | '7d'
const RANGES: HistRange[] = ['live', '5m', '15m', '1h', '6h', '24h', '7d']

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
  const info = infoFor(widget.key)
  const [rows, setRows] = useState<TagStat[] | null>(null)
  const [fullScale, setFullScale] = useState(false)
  const [range, setRange] = useState<HistRange>('live')
  const [hist, setHist] = useState<MetricHistory | null>(null)
  const [histOk, setHistOk] = useState(true)
  const fmt = useTimeFmt()
  const t = useT()

  useEffect(() => {
    if (!bd) return
    let active = true
    fetchMetricBreakdown(serviceId, bd.metric, bd.tag, bd.filter)
      .then((r) => { if (active) setRows(r) })
      .catch(() => { if (active) setRows([]) })
    return () => { active = false }
  }, [serviceId, bd])

  // Persisted history for a selected range (opt-in DB). 'live' uses the frontend series; a 404
  // (history disabled) falls back to live and hides the presets.
  useEffect(() => {
    if (range === 'live') { setHist(null); return }
    let active = true
    const load = () => fetchMetricHistory(serviceId, widget.key, range)
      .then((h) => { if (active) { setHist(h); setHistOk(true) } })
      .catch(() => { if (active) { setHistOk(false); setRange('live') } })
    load()
    const tmr = setInterval(load, 15000)
    return () => { active = false; clearInterval(tmr) }
  }, [serviceId, widget.key, range])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // Rate widgets (perSecond) show the current rate (last series point), not the raw cumulative total.
  const currentValue = widget.perSecond ? (series.length ? series[series.length - 1] : null) : widget.value
  const historical = range !== 'live'
  const chartData = historical ? (hist?.points.map((p) => p.v) ?? []) : series
  const chartTs = historical ? hist?.points.map((p) => p.t) : undefined
  const nums = chartData.filter((n) => Number.isFinite(n))
  const stats = nums.length
    ? { min: Math.min(...nums), max: Math.max(...nums), avg: nums.reduce((a, b) => a + b, 0) / nums.length }
    : null

  return (
    <div className="metric-view">
      <header className="detail-header">
        <button className="detail-back" onClick={onClose} aria-label={t('detail.backAria')}>← {serviceName}</button>
        <h2 className="detail-title">{widget.label}</h2>
        <span className="mi__current">
          {formatValue(currentValue, widget.unit)}{widget.perSecond && currentValue != null ? '/s' : ''}
        </span>
      </header>

      <div className="metric-view__cols">
        <div className="metric-view__main">
          <section className="metric-panel">
            <div className="alert-drawer__label mi__trendhead">
              <span>
                {historical
                  ? t('trend.historical', { range, n: nums.length })
                  : (widget.cumulative && !widget.perSecond
                      ? t('trend.liveDelta', { n: series.length })
                      : t('trend.live', { n: series.length }))}
              </span>
              <span className="mi__trendctl">
                <span className="mi__ranges">
                  {RANGES.filter((r) => r === 'live' || histOk).map((r) => (
                    <button key={r} className={range === r ? 'on' : ''} onClick={() => setRange(r)}>{r}</button>
                  ))}
                </span>
                <TimeFormatSelect />
                <span className="mi__scaletoggle">
                  <button className={!fullScale ? 'on' : ''} onClick={() => setFullScale(false)}>{t('scale.auto')}</button>
                  <button className={fullScale ? 'on' : ''} onClick={() => setFullScale(true)}>{widget.unit === 'PERCENT' ? t('scale.fullPercent') : t('scale.fullMax')}</button>
                </span>
              </span>
            </div>
            {!widget.available ? (
              <div className="mi__muted">{t('metric.unavailable')}</div>
            ) : chartData.length > 1 ? (
              <>
                <div className="mi__chart"><LiveChart data={chartData} timestamps={chartTs} unit={widget.unit} height={260} showTime fullScale={fullScale} intervalSec={5} timeFmt={fmt} /></div>
                {stats && (
                  <div className="mi__stats">
                    <span>{t('stats.min')} <b>{formatValue(stats.min, widget.unit)}</b></span>
                    <span>{t('stats.avg')} <b>{formatValue(stats.avg, widget.unit)}</b></span>
                    <span>{t('stats.max')} <b>{formatValue(stats.max, widget.unit)}</b></span>
                  </div>
                )}
              </>
            ) : (
              <div className="mi__muted">{historical ? t('hist.none', { range }) : t('collecting')}</div>
            )}
          </section>

          {bd && (
            <section className="metric-panel">
              <div className="alert-drawer__label">{t(bd.titleKey)} {t('breakdown.totals')}</div>
              {rows == null ? (
                <div className="mi__muted">{t('loading')}</div>
              ) : rows.length === 0 ? (
                <div className="mi__muted">{t('noData')}</div>
              ) : (
                <table className="mi__table">
                  <thead><tr><th>{t('table.endpoint')}</th><th>{t('table.count')}</th><th>{t('table.avg')}</th><th>{t('table.max')}</th></tr></thead>
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
          {info && (
            <section className="metric-panel mi__info">
              <div className="alert-drawer__label">{t('info.about')}</div>
              <p className="mi__info-desc">{t('metric.' + widget.key + '.desc')}</p>
              {info.hasTip && <p className="mi__info-tip"><b>{t('info.tip')}</b> — {t('metric.' + widget.key + '.tip')}</p>}
            </section>
          )}
          {rule ? (
            <AlertSection rule={rule} current={quantity(widget)} recent={recent} onSave={onSaveAlert} />
          ) : (
            <section className="metric-panel"><div className="alert-drawer__label">{t('alert.title')}</div><div className="mi__muted">{t('alert.none')}</div></section>
          )}
        </div>
      </div>
    </div>
  )
}

/** The alert-rule editor: condition + live status + threshold slider + "for" duration + cooldown. */
function AlertSection({ rule, current, recent, onSave }: { rule: AlertRule; current: number | null; recent: AlertFire[]; onSave: (patch: SavePatch) => void }) {
  const isPercent = rule.unit === 'PERCENT'
  const toDisplay = (thr: number) => (isPercent ? Math.round(thr * 100) : thr)
  const fromDisplay = (d: number) => (isPercent ? d / 100 : d)

  const [enabled, setEnabled] = useState(rule.enabled)
  const [threshold, setThreshold] = useState(toDisplay(rule.threshold))
  const [forSecs, setForSecs] = useState(rule.forSeconds)
  const [cooldown, setCooldown] = useState(rule.cooldownSeconds)
  const [saved, setSaved] = useState(false)
  const fmt = useTimeFmt()
  const t = useT()

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
      <div className="alert-drawer__label">{t('alert.title')}</div>
      <div className="alert-cond">
        <span className="alert-cond__metric">{rule.label}</span>
        <span className="alert-cond__op">{cmp}</span>
        <span className="alert-cond__th">{threshold}{isPercent ? '%' : ''}</span>
        {forSecs > 0 && <span className="alert-cond__for">{t('alert.forCond', { n: forSecs })}</span>}
      </div>
      <div className={`alert-status alert-status--${!enabled ? 'off' : breaching ? 'bad' : 'ok'}`}>
        {t('alert.currently')} <b>{current != null ? formatValue(current, rule.unit) : '—'}</b>
        <span>· {!enabled ? t('status.disabled') : breaching ? t('status.breaching') : t('status.ok')}</span>
      </div>
      <label className="alert-field alert-field--toggle">
        <span>{t('alert.enabled')}</span>
        <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
      </label>
      <div className="alert-field">
        <span className="alert-field__label">{t('alert.threshold', { cmp, threshold, unit: isPercent ? '%' : '' })}</span>
        <div className="alert-field__inline">
          {isPercent && <input type="range" min={0} max={100} value={threshold} onChange={(e) => setThreshold(Number(e.target.value))} />}
          <input type="number" value={threshold} onChange={(e) => setThreshold(Number(e.target.value))} />
          {isPercent && <span className="alert-field__unit">%</span>}
        </div>
      </div>
      <div className="alert-field">
        <span className="alert-field__label">{t('alert.for')}</span>
        <div className="alert-field__inline">
          <input type="number" value={forSecs} onChange={(e) => setForSecs(Number(e.target.value))} />
          <span className="alert-field__unit">{t('alert.forUnit')}</span>
        </div>
      </div>
      <div className="alert-field">
        <span className="alert-field__label">{t('alert.cooldown')}</span>
        <div className="alert-field__inline">
          <input type="number" value={cooldown} onChange={(e) => setCooldown(Number(e.target.value))} />
          <span className="alert-field__unit">{t('alert.cooldownUnit')}</span>
        </div>
      </div>
      {recent.length > 0 && (
        <div className="mi__fires">
          <div className="alert-drawer__label mi__fireshead"><span>{t('alert.recentFires')}</span><TimeFormatSelect /></div>
          {recent.slice(0, 6).map((a, i) => (
            <div key={i} className="alert-drawer__fire"><b>{formatValue(a.value, a.unit)}</b> vs {formatValue(a.threshold, a.unit)} · {formatTime(a.at, fmt)}</div>
          ))}
        </div>
      )}
      <div className="alert-drawer__actions">
        <span className="alert-drawer__saved">{saved ? t('alert.saved') : ''}</span>
        <button className="alert-drawer__save" onClick={save}>{t('alert.save')}</button>
      </div>
    </section>
  )
}
