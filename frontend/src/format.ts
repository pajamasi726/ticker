import type { Unit } from './types'

const KB = 1024
const MB = KB * 1024
const GB = MB * 1024

function formatBytes(bytes: number): string {
  if (bytes < 0) return '—'
  if (bytes < KB) return `${bytes.toFixed(0)} B`
  if (bytes < MB) return `${(bytes / KB).toFixed(0)} KB`
  if (bytes < GB) return `${(bytes / MB).toFixed(0)} MB`
  return `${(bytes / GB).toFixed(2)} GB`
}

function formatDuration(seconds: number): string {
  if (seconds < 1) return `${(seconds * 1000).toFixed(0)} ms`
  if (seconds < 60) return `${seconds.toFixed(1)} s`
  const m = Math.floor(seconds / 60)
  if (m < 60) return `${m}m ${Math.floor(seconds % 60)}s`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ${m % 60}m`
  return `${Math.floor(h / 24)}d ${h % 24}h`
}

/** Generic value formatter, driven entirely by the backend's `unit` — replaces per-metric switches. */
export function formatValue(value: number | null, unit: Unit): string {
  if (value == null) return '—'
  switch (unit) {
    case 'BYTES':
      return formatBytes(value)
    case 'PERCENT':
      return `${(value <= 1 ? value * 100 : value).toFixed(1)}%`
    case 'COUNT':
      return Math.round(value).toLocaleString()
    case 'SECONDS':
      return formatDuration(value)
    case 'MILLIS':
      return `${Math.round(value).toLocaleString()} ms`
    case 'TIMESTAMP':
      return new Date(value * 1000).toLocaleTimeString()
  }
}
