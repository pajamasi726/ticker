import { useEffect, useRef } from 'react'
import uPlot from 'uplot'
import 'uplot/dist/uPlot.min.css'
import type { Unit } from '../types'
import type { TimeFmt } from '../timeFormat'
import { formatValue } from '../format'

const STROKE = '#5b8def'

/** Compact, unit-aware y-axis tick labels (e.g. "59MB", "0.3%", "3ms"). */
function tickValues(unit: Unit) {
  return (_u: uPlot, splits: number[]) => splits.map((v) => formatValue(v, unit).replace(/\s+/g, ''))
}

/**
 * X-axis labels from the sample index + poll interval. 'relative' → "now"/"-30s"/"-2m";
 * any absolute format → wall-clock "HH:mm:ss" (the last sample ≈ now, each step back one interval).
 */
function timeValues(intervalSec: number, fmt: TimeFmt) {
  const pad = (x: number) => String(x).padStart(2, '0')
  return (u: uPlot, splits: number[]) => {
    const n = u.data[0].length
    const now = Date.now()
    return splits.map((v) => {
      if (fmt === 'relative') {
        const back = Math.round((n - 1 - v) * intervalSec)
        return back <= 0 ? 'now' : back < 60 ? `-${back}s` : `-${Math.round(back / 60)}m`
      }
      const d = new Date(now - (n - 1 - v) * intervalSec * 1000)
      return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    })
  }
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

interface Props {
  data: number[]
  unit: Unit
  height?: number
  showTime?: boolean // render a time x-axis
  fullScale?: boolean // fixed y-axis (0-100% for PERCENT, else 0-max) instead of autoscale
  intervalSec?: number // poll interval, for the time axis
  timeFmt?: TimeFmt // x-axis time style: 'relative' (-30s) or absolute clock (HH:mm:ss)
}

/**
 * Live time-series panel. Default y-axis autoscales to the data range (near-constant series keep
 * shape); `fullScale` pins it (0–100% for percent, else 0–max). Area-gradient fill, unit-formatted
 * y ticks, optional relative-time x-axis, and a hover tooltip. Width is responsive.
 */
export function LiveChart({ data, unit, height = 88, showTime = false, fullScale = false, intervalSec = 5, timeFmt = 'relative' }: Props) {
  const el = useRef<HTMLDivElement>(null)
  const plot = useRef<uPlot | null>(null)

  useEffect(() => {
    if (!el.current) return
    const width = el.current.clientWidth || 240
    const percent = unit === 'PERCENT'
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
            if (fullScale) return percent ? [0, 1] : [0, max > 0 ? max : 1]
            if (min === max) { const p = Math.abs(min) * 0.1 || 1; return [Math.min(0, min), max + p] }
            const pad = (max - min) * 0.14
            const lo = min - pad
            return [min >= 0 && lo < 0 ? 0 : lo, max + pad]
          },
        },
      },
      axes: [
        showTime
          ? { show: true, size: 22, stroke: '#5b6472', font: '10px ui-monospace, monospace', ticks: { show: false }, grid: { show: false }, values: timeValues(intervalSec, timeFmt), space: 64 }
          : { show: false },
        {
          show: true,
          size: 44,
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
  }, [height, unit, showTime, fullScale, intervalSec, timeFmt])

  useEffect(() => { plot.current?.setData([data.map((_, i) => i), data]) }, [data])

  return <div ref={el} className="live-chart-wrap" />
}
