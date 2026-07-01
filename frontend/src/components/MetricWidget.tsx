import type { ResolvedWidget, AlertRule } from '../types'
import { formatValue } from '../format'
import { infoFor } from '../metricInfo'
import { LiveChart } from './LiveChart'
import { Gauge } from './Gauge'
import { AlertBell } from './AlertBell'
import { useT } from '../i18n'

interface MetricWidgetProps {
  widget: ResolvedWidget
  series: number[]
  alertRule?: AlertRule | null
  onOpen?: (key: string) => void
}

/** Generic renderer: GAUGE -> Gauge, CHART -> LiveChart + current value, NUMBER -> big value.
 *  Card is clickable → metric inspector; head shows ⓘ (info) and 🔔 (alert). `important` metrics
 *  (e.g. Full GC, GC overhead, error rate) get an accent so they stand out. */
export function MetricWidget({ widget, series, alertRule, onOpen }: MetricWidgetProps) {
  const open = onOpen ? () => onOpen(widget.key) : undefined
  const info = infoFor(widget.key)
  const important = !!info?.important
  const t = useT()

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
  const controls = infoBtn || bell ? <>{infoBtn}{bell}</> : null

  const interactive = open
    ? {
        onClick: open,
        role: 'button' as const,
        tabIndex: 0,
        onKeyDown: (e: React.KeyboardEvent) => { if (e.key === 'Enter' || e.key === ' ') open() },
      }
    : {}
  const cls = `${open ? ' widget--clickable' : ''}${important ? ' widget--important' : ''}`

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
        important={important}
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
        {controls}
      </div>
      <div className="widget__value widget__value--big">{formatValue(widget.value, widget.unit)}</div>
    </div>
  )
}
