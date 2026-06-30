import { useEffect, useRef } from 'react'
import uPlot from 'uplot'
import 'uplot/dist/uPlot.min.css'
import type { Unit } from '../types'
import { formatValue } from '../format'

const STROKE = '#5b8def'

/** Compact, unit-aware y-axis tick labels (e.g. "59MB", "0.3%", "3ms"). */
function tickValues(unit: Unit) {
  return (_u: uPlot, splits: number[]) => splits.map((v) => formatValue(v, unit).replace(/\s+/g, ''))
}

/** Floating value tooltip on hover — the Grafana "point at the line, read the value" feel. */
function tooltipPlugin(unit: Unit): uPlot.Plugin {
  let tip: HTMLDivElement | null = null
  return {
    hooks: {
      init: (u) => {
        tip = document.createElement('div')
        tip.className = 'uchart-tip'
        tip.style.display = 'none'
        u.over.appendChild(tip)
      },
      setCursor: (u) => {
        if (!tip) return
        const idx = u.cursor.idx
        const val = idx != null ? (u.data[1][idx] as number | null | undefined) : null
        if (idx == null || val == null) { tip.style.display = 'none'; return }
        tip.textContent = formatValue(val, unit)
        tip.style.display = 'block'
        tip.style.left = `${u.cursor.left ?? 0}px`
        tip.style.top = `${u.cursor.top ?? 0}px`
      },
    },
  }
}

interface Props { data: number[]; unit: Unit; height?: number }

/**
 * Live time-series panel. Autoscales Y to the data range (with padding) so near-constant series
 * keep their shape; 0-anchored when the data is non-negative. Area-gradient fill, subtle gridlines,
 * unit-formatted y ticks, and a hover tooltip. Width is responsive via ResizeObserver.
 */
export function LiveChart({ data, unit, height = 88 }: Props) {
  const el = useRef<HTMLDivElement>(null)
  const plot = useRef<uPlot | null>(null)

  useEffect(() => {
    if (!el.current) return
    const width = el.current.clientWidth || 240
    const opts: uPlot.Options = {
      width,
      height,
      class: 'live-chart',
      cursor: { show: true, x: true, y: false, points: { show: true, size: 6 } },
      legend: { show: false },
      scales: {
        x: { time: false },
        y: {
          range: (_u, min, max) => {
            if (min === max) { const p = Math.abs(min) * 0.1 || 1; return [Math.min(0, min), max + p] }
            const pad = (max - min) * 0.14
            const lo = min - pad
            return [min >= 0 && lo < 0 ? 0 : lo, max + pad]
          },
        },
      },
      axes: [
        { show: false },
        {
          show: true,
          size: 42,
          gap: 3,
          stroke: '#5b6472',
          font: '10px ui-monospace, monospace',
          ticks: { show: false },
          grid: { show: true, stroke: '#20262f', width: 1 },
          values: tickValues(unit),
          space: 28,
        },
      ],
      series: [
        {},
        {
          stroke: STROKE,
          width: 1.75,
          fill: (u) => {
            const g = u.ctx.createLinearGradient(0, u.bbox.top, 0, u.bbox.top + u.bbox.height)
            g.addColorStop(0, `${STROKE}59`)
            g.addColorStop(1, `${STROKE}00`)
            return g
          },
          points: { show: false },
        },
      ],
      plugins: [tooltipPlugin(unit)],
    }
    plot.current = new uPlot(opts, [data.map((_, i) => i), data], el.current)
    const ro = new ResizeObserver(() => {
      const w = el.current?.clientWidth ?? 0
      if (w > 0 && plot.current) plot.current.setSize({ width: w, height })
    })
    ro.observe(el.current)
    return () => { ro.disconnect(); plot.current?.destroy(); plot.current = null }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [height, unit])

  useEffect(() => { plot.current?.setData([data.map((_, i) => i), data]) }, [data])

  return <div ref={el} className="live-chart-wrap" />
}
