import { useEffect, useState } from 'react'
import { useT } from '../i18n'

const KEY = 'ticker-ui-zoom'
const STEPS = [50, 67, 75, 80, 90, 100, 110, 125, 150, 175, 200]

/** Everyone starts at 100%; whatever they pick is remembered per browser and restored next visit. */
function initialZoom(): number {
  try {
    const stored = Number(localStorage.getItem(KEY))
    if (STEPS.includes(stored)) return stored
  } catch { /* ignore */ }
  return 100
}

/**
 * Chrome-style in-app zoom: − / current % / + next to the gear. Applied as CSS `zoom` on <body>
 * (NOT <html> — html-level zoom is what made the page bottom unreachable under some browser-zoom
 * combos), persisted per browser. Clicking the percentage snaps back to 100%.
 */
export function ZoomControl() {
  const t = useT()
  const [zoom, setZoom] = useState(initialZoom)

  useEffect(() => {
    document.body.style.setProperty('zoom', String(zoom / 100))
    try { localStorage.setItem(KEY, String(zoom)) } catch { /* ignore */ }
  }, [zoom])

  const step = (dir: 1 | -1) => {
    const i = STEPS.indexOf(zoom)
    const at = i === -1 ? STEPS.indexOf(100) : i
    setZoom(STEPS[Math.min(STEPS.length - 1, Math.max(0, at + dir))])
  }

  return (
    <span className="zoomctl" role="group" aria-label={t('zoom.label')}>
      <button onClick={() => step(-1)} disabled={zoom === STEPS[0]} aria-label={t('zoom.out')} title={t('zoom.out')}>−</button>
      <button className="zoomctl__pct" onClick={() => setZoom(100)} title={t('zoom.reset')}>{zoom}%</button>
      <button onClick={() => step(1)} disabled={zoom === STEPS[STEPS.length - 1]} aria-label={t('zoom.in')} title={t('zoom.in')}>+</button>
    </span>
  )
}
