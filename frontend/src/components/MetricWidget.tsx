import type { ResolvedWidget } from '../types'
import { formatValue } from '../format'
import { LiveChart } from './LiveChart'
import { Gauge } from './Gauge'

interface MetricWidgetProps {
  widget: ResolvedWidget
  series: number[]
}

/** Generic renderer: GAUGE -> Gauge, CHART -> LiveChart + current value, NUMBER -> big value. */
export function MetricWidget({ widget, series }: MetricWidgetProps) {
  if (widget.render === 'GAUGE') {
    return (
      <Gauge
        label={widget.label}
        value={widget.value}
        max={widget.max}
        unit={widget.unit}
        higherIsBetter={widget.higherIsBetter}
      />
    )
  }
  if (widget.render === 'CHART') {
    const current = series.length > 0 ? series[series.length - 1] : widget.value
    return (
      <div className="widget widget--chart">
        <div className="widget__head">
          <span className="widget__label">{widget.label}</span>
          <span className="widget__value">
            {formatValue(current ?? null, widget.unit)}{widget.perSecond && current != null ? '/s' : ''}
          </span>
        </div>
        <LiveChart data={series} unit={widget.unit} />
      </div>
    )
  }
  return (
    <div className="widget widget--number">
      <div className="widget__label">{widget.label}</div>
      <div className="widget__value widget__value--big">{formatValue(widget.value, widget.unit)}</div>
    </div>
  )
}
