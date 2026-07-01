import { useSyncExternalStore } from 'react'
import { METRIC_INFO } from './metricInfo'

// User's "important to me" set (a star toggle), persisted. Seeded from the curated `important`
// metrics so the ones we consider key are starred by default; the user can add/remove freely.
const KEY = 'ticker.starred'

const defaultStarred = (): string[] =>
  Object.entries(METRIC_INFO).filter(([, v]) => v.important).map(([k]) => k)

function load(): Set<string> {
  try {
    const s = localStorage.getItem(KEY)
    if (s) return new Set(JSON.parse(s) as string[])
  } catch { /* localStorage unavailable */ }
  return new Set(defaultStarred())
}

let current = load()
let snapshot: readonly string[] = [...current].sort()
const listeners = new Set<() => void>()

function commit() {
  snapshot = [...current].sort()
  try { localStorage.setItem(KEY, JSON.stringify(snapshot)) } catch { /* ignore */ }
  listeners.forEach((cb) => cb())
}

/** Toggle a metric's star (persisted). */
export function toggleStar(key: string) {
  if (current.has(key)) current.delete(key)
  else current.add(key)
  commit()
}

/** React hook: sorted list of starred keys, stable between changes. Use `.includes(key)`. */
export function useStarred(): readonly string[] {
  return useSyncExternalStore(
    (l) => { listeners.add(l); return () => { listeners.delete(l) } },
    () => snapshot,
  )
}
