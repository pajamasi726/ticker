interface Props { values: (number | null)[]; width?: number; height?: number }

export function Sparkline({ values, width = 92, height = 26 }: Props) {
  const nums = values.filter((v): v is number => v != null)
  if (nums.length === 0) {
    return (
      <svg width={width} height={height} className="sparkline sparkline--empty" aria-hidden>
        <line x1={0} y1={height - 1} x2={width} y2={height - 1} />
      </svg>
    )
  }
  const max = Math.max(...nums, 1) // 0-anchored: small values read small, no per-tile min/max amplification
  const step = values.length > 1 ? width / (values.length - 1) : width
  const y = (v: number) => height - (v / max) * (height - 3) - 1
  const segments: string[] = []
  const fails: number[] = []
  let cur: string[] = []
  values.forEach((v, i) => {
    const x = i * step
    if (v == null) {
      fails.push(x)
      if (cur.length) { segments.push(cur.join(' ')); cur = [] }
    } else {
      cur.push(`${x.toFixed(1)},${y(v).toFixed(1)}`)
    }
  })
  if (cur.length) segments.push(cur.join(' '))
  return (
    <svg width={width} height={height} className="sparkline" aria-hidden>
      {segments.map((pts, i) => <polyline key={i} points={pts} />)}
      {fails.map((x, i) => <line key={`f${i}`} className="sparkline__fail" x1={x} y1={height - 5} x2={x} y2={height - 1} />)}
    </svg>
  )
}
