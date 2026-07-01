import type { ResolvedWidget, AlertRule } from '../types'
import { formatValue } from '../format'
import { infoFor } from '../metricInfo'
import { LiveChart } from './LiveChart'
import { Gauge } from './Gauge'
import { AlertBell } from './AlertBell'
import { useT } from '../i18n'
import { useStarred, toggleStar } from '../starred'
import { severityOf } from '../severity'

interface MetricWidgetProps {
  widget: ResolvedWidget
  series: number[]
  alertRule?: AlertRule | null
  onOpen?: (key: string) => void
}

/** Generic renderer: GAUGE -> Gauge, CHART -> LiveChart + current value, NUMBER -> big value.
 *  Card is clickable → metric inspector; head shows ★ (star/important toggle), ⓘ (info), 🔔 (alert).
 *  The colored left bar means SEVERITY (warn/crit), driven by the alert threshold or gauge value/max. */
export function MetricWidget({ widget, series, alertRule, onOpen }: MetricWidgetProps) {
  const open = onOpen ? () => onOpen(widget.key) : undefined
  const info = infoFor(widget.key)
  const t = useT()
  const starred = useStarred().includes(widget.key)
  const sev = severityOf(widget, alertRule)

  // ★ = "important to me" (a persisted user toggle) — distinct from the severity bar, which is value-driven.
  const starBtn = (
    <button
      type="button"
      className={`widget__star${starred ? ' on' : ''}`}
      title={t(starred ? 'star.on' : 'star.off')}
      aria-label={t(starred ? 'star.on' : 'star.off')}
      aria-pressed={starred}
      onClick={(e) => { e.stopPropagation(); toggleStar(widget.key) }}
    >{starred ? '★' : '☆'}</button>
  )
  const infoBtn = info && open ? (
    <button
      type="button"
      className="info-btn"
      title={t('metric.' + widget.key + '.desc')}
      aria-label="Metric info"
      onClick={(e) => { e.stopPropagation(); open() }}
    >ⓘ</button>
  ) : null
  const bell = alertRule && onOpen ? <AlertBell rule={alertRule} onOpen={onOpen} /> : null
  const controls = <>{starBtn}{infoBtn}{bell}</>

  const interactive = open
    ? {
        onClick: open,
        role: 'button' as const,
        tabIndex: 0,
        onKeyDown: (e: React.KeyboardEvent) => { if (e.key === 'Enter' || e.key === ' ') open() },
      }
    : {}
  const sevCls = sev === 'crit' ? ' widget--crit' : sev === 'warn' ? ' widget--warn' : ''
  const cls = `${open ? ' widget--clickable' : ''}${sevCls}`

  // Not collected on this target: show the widget dimmed with a note (keeps the full catalog visible)
  // instead of hiding it. Still clickable + ⓘ so its "about" info explains what you're missing.
  if (!widget.available) {
    return (
      <div className={`widget widget--number widget--unavailable${open ? ' widget--clickable' : ''}`} {...interactive}>
        <div className="widget__head">
          <span className="widget__label">{widget.label}</span>
          <span className="widget__head-end">{starBtn}{infoBtn}</span>
        </div>
        <div className="widget__na">{t('metric.unavailable')}</div>
      </div>
    )
  }

  if (widget.render === 'GAUGE') {
    return (
      <Gauge
        label={widget.label}
        value={widget.value}
        max={widget.max}
        unit={widget.unit}
        higherIsBetter={widget.higherIsBetter}
        controls={controls}
        onClick={open}
        severity={sev}
      />
    )
  }
  if (widget.render === 'CHART') {
    const current = series.length > 0 ? series[series.length - 1] : widget.value
    return (
      <div className={`widget widget--chart${cls}`} {...interactive}>
        <div className="widget__head">
          <span className="widget__label">{widget.label}</span>
          <span className="widget__head-end">
            <span className="widget__value">
              {formatValue(current ?? null, widget.unit)}{widget.perSecond && current != null ? '/s' : ''}
            </span>
            {controls}
          </span>
        </div>
        <LiveChart data={series} unit={widget.unit} />
      </div>
    )
  }
  return (
    <div className={`widget widget--number${cls}`} {...interactive}>
      <div className="widget__head">
        <span className="widget__label">{widget.label}</span>
        <span className="widget__head-end">{controls}</span>
      </div>
      <div className="widget__value widget__value--big">{formatValue(widget.value, widget.unit)}</div>
    </div>
  )
}
