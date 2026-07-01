import { useSyncExternalStore } from 'react'

// User-selectable timestamp format for alert fires (persisted). "relative" = "3m ago".
export type TimeFmt = 'relative' | 'HH:mm:ss' | 'dd HH:mm:ss' | 'MM-dd HH:mm:ss' | 'yyyy-MM-dd HH:mm:ss'

export const TIME_FMTS: TimeFmt[] = ['yyyy-MM-dd HH:mm:ss', 'MM-dd HH:mm:ss', 'dd HH:mm:ss', 'HH:mm:ss', 'relative']

const KEY = 'ticker.timeFmt'
const isFmt = (v: unknown): v is TimeFmt => typeof v === 'string' && (TIME_FMTS as string[]).includes(v)

let current: TimeFmt = 'yyyy-MM-dd HH:mm:ss'
try {
  const saved = localStorage.getItem(KEY)
  if (isFmt(saved)) current = saved
} catch { /* localStorage unavailable — keep default */ }

const listeners = new Set<() => void>()

export function setTimeFmt(f: TimeFmt) {
  current = f
  try { localStorage.setItem(KEY, f) } catch { /* ignore */ }
  listeners.forEach((l) => l())
}

/** React hook: current format, re-rendering when it changes anywhere. */
export function useTimeFmt(): TimeFmt {
  return useSyncExternalStore(
    (l) => { listeners.add(l); return () => { listeners.delete(l) } },
    () => current,
  )
}

const pad = (n: number) => String(n).padStart(2, '0')

/** Format an ISO instant per the chosen format. Locale-stable (built from date parts). */
export function formatTime(iso: string, fmt: TimeFmt): string {
  const t = Date.parse(iso)
  if (Number.isNaN(t)) return ''
  if (fmt === 'relative') {
    const s = Math.max(0, Math.round((Date.now() - t) / 1000))
    return s < 60 ? `${s}s ago` : `${Math.round(s / 60)}m ago`
  }
  const d = new Date(t)
  const time = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  switch (fmt) {
    case 'HH:mm:ss': return time
    case 'dd HH:mm:ss': return `${pad(d.getDate())} ${time}`
    case 'MM-dd HH:mm:ss': return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${time}`
    case 'yyyy-MM-dd HH:mm:ss': return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${time}`
  }
}
