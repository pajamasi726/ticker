import type { ServiceView, ServiceDetail, AlertRule, AlertFire, TagStat } from './types'

export async function fetchServices(): Promise<ServiceView[]> {
  const res = await fetch('/api/services')
  if (!res.ok) throw new Error(`GET /api/services failed: ${res.status}`)
  return res.json()
}

export async function fetchDetail(id: string): Promise<ServiceDetail> {
  const res = await fetch(`/api/services/${encodeURIComponent(id)}/detail`)
  if (!res.ok) throw new Error(`detail ${id}: ${res.status}`)
  return res.json()
}

export async function fetchAlertRules(): Promise<AlertRule[]> {
  const res = await fetch('/api/alerts/rules')
  if (!res.ok) throw new Error(`GET /api/alerts/rules: ${res.status}`)
  return res.json()
}

export async function updateAlertRule(
  key: string,
  patch: { enabled?: boolean; threshold?: number; cooldownSeconds?: number; forSeconds?: number },
): Promise<AlertRule> {
  const res = await fetch(`/api/alerts/rules/${encodeURIComponent(key)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(patch),
  })
  if (!res.ok) throw new Error(`PUT /api/alerts/rules/${key}: ${res.status}`)
  return res.json()
}

export async function fetchRecentAlerts(): Promise<AlertFire[]> {
  const res = await fetch('/api/alerts/recent')
  if (!res.ok) throw new Error(`GET /api/alerts/recent: ${res.status}`)
  return res.json()
}

export async function fetchMetricBreakdown(id: string, metric: string, tag: string, filter?: string): Promise<TagStat[]> {
  const q = new URLSearchParams({ metric, tag })
  if (filter) q.set('filter', filter)
  const res = await fetch(`/api/services/${encodeURIComponent(id)}/metric-breakdown?${q.toString()}`)
  if (!res.ok) throw new Error(`GET metric-breakdown ${metric}/${tag}: ${res.status}`)
  return res.json()
}
