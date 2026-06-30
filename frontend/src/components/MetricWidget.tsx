import type { ResolvedWidget, AlertRule } from '../types'
import { formatValue } from '../format'
import { LiveChart } from './LiveChart'
import { Gauge } from './Gauge'
import { AlertBell } from './AlertBell'

interface MetricWidgetProps {
  widget: ResolvedWidget
  series: number[]
  alertRule?: AlertRule | null
  onAlertSave?: (key: string, patch: { enabled?: boolean; threshold?: number; cooldownSeconds?: number }) => void
}

/** Generic renderer: GAUGE -> Gauge, CHART -> LiveChart + current value, NUMBER -> big value. */
export function MetricWidget({ widget, series, alertRule, onAlertSave }: MetricWidgetProps) {
  const bell = alertRule && onAlertSave ? <AlertBell rule={alertRule} onSave={onAlertSave} /> : null

  if (widget.render === 'GAUGE') {
    return (
      <Gauge
        label={widget.label}
        value={widget.value}
        max={widget.max}
        unit={widget.unit}
        higherIsBetter={widget.higherIsBetter}
        bell={bell}
      />
    )
  }
  if (widget.render === 'CHART') {
    const current = series.length > 0 ? series[series.length - 1] : widget.value
    return (
      <div className="widget widget--chart">
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
    <div className="widget widget--number">
      <div className="widget__head">
        <span className="widget__label">{widget.label}</span>
        {bell}
      </div>
      <div className="widget__value widget__value--big">{formatValue(widget.value, widget.unit)}</div>
    </div>
  )
}
