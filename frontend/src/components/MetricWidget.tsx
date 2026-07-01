import type { ResolvedWidget, AlertRule } from '../types'
import { formatValue } from '../format'
import { LiveChart } from './LiveChart'
import { Gauge } from './Gauge'
import { AlertBell } from './AlertBell'

interface MetricWidgetProps {
  widget: ResolvedWidget
  series: number[]
  alertRule?: AlertRule | null
  onOpen?: (key: string) => void
}

/** Generic renderer: GAUGE -> Gauge, CHART -> LiveChart + current value, NUMBER -> big value.
 *  The whole card is clickable → opens the metric inspector; the 🔔 opens it too (to the alert). */
export function MetricWidget({ widget, series, alertRule, onOpen }: MetricWidgetProps) {
  const open = onOpen ? () => onOpen(widget.key) : undefined
  const bell = alertRule && onOpen ? <AlertBell rule={alertRule} onOpen={onOpen} /> : null
  const interactive = open
    ? {
        onClick: open,
        role: 'button' as const,
        tabIndex: 0,
        onKeyDown: (e: React.KeyboardEvent) => { if (e.key === 'Enter' || e.key === ' ') open() },
      }
    : {}
  const clickable = open ? ' widget--clickable' : ''

  if (widget.render === 'GAUGE') {
    return (
      <Gauge
        label={widget.label}
        value={widget.value}
        max={widget.max}
        unit={widget.unit}
        higherIsBetter={widget.higherIsBetter}
        bell={bell}
        onClick={open}
      />
    )
  }
  if (widget.render === 'CHART') {
    const current = series.length > 0 ? series[series.length - 1] : widget.value
    return (
      <div className={`widget widget--chart${clickable}`} {...interactive}>
        <div className="widget__head">
          <span className="widget__label">{widget.label}</span>
          <span className="widget__head-end">
            <span className="widget__value">
              {formatValue(current ?? null, widget.unit)}{widget.perSecond && current != null ? '/s' : ''}
            </span>
            {bell}
          </span>
        </div>
        <LiveChart data={series} unit={widget.unit} />
      </div>
    )
  }
  return (
    <div className={`widget widget--number${clickable}`} {...interactive}>
      <div className="widget__head">
        <span className="widget__label">{widget.label}</span>
        {bell}
      </div>
      <div className="widget__value widget__value--big">{formatValue(widget.value, widget.unit)}</div>
    </div>
  )
}
