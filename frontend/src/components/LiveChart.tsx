import { useEffect, useRef } from 'react'
import uPlot from 'uplot'
import 'uplot/dist/uPlot.min.css'

interface Props { data: number[]; width?: number; height?: number; color?: string }

export function LiveChart({ data, width = 220, height = 46, color = '#5b8def' }: Props) {
  const el = useRef<HTMLDivElement>(null)
  const plot = useRef<uPlot | null>(null)

  useEffect(() => {
    if (!el.current) return
    const opts: uPlot.Options = {
      width, height, class: 'live-chart',
      cursor: { show: false }, legend: { show: false },
      scales: { x: { time: false }, y: { range: (_u, _min, max) => [0, max > 0 ? max : 1] } },
      axes: [{ show: false }, { show: false }],
      series: [{}, { stroke: color, width: 1.5, fill: `${color}22`, points: { show: false } }],
    }
    plot.current = new uPlot(opts, [data.map((_, i) => i), data], el.current)
    return () => { plot.current?.destroy(); plot.current = null }
  }, [width, height, color])

  useEffect(() => { plot.current?.setData([data.map((_, i) => i), data]) }, [data])

  return <div ref={el} className="live-chart-wrap" />
}
