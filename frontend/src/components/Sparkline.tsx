interface Props {
  values: (number | null)[]
  width?: number
  height?: number
}

// Placeholder heartbeat: an inline SVG polyline. Real charting (uPlot) lands in P5/P7.
export function Sparkline({ values, width = 88, height = 24 }: Props) {
  const nums = values.filter((v): v is number => v != null)
  if (nums.length === 0) {
    return (
      <svg width={width} height={height} className="sparkline sparkline--empty" aria-hidden>
        <line x1={0} y1={height / 2} x2={width} y2={height / 2} />
      </svg>
    )
  }
  const max = Math.max(...nums)
  const min = Math.min(...nums)
  const span = max - min || 1
  const step = values.length > 1 ? width / (values.length - 1) : width
  const points = values
    .map((v, i) => (v == null ? null : `${i * step},${height - ((v - min) / span) * height}`))
    .filter((p): p is string => p != null)
    .join(' ')
  return (
    <svg width={width} height={height} className="sparkline" aria-hidden>
      <polyline fill="none" points={points} />
    </svg>
  )
}
