import type { ServiceView, ServiceDetail, AlertRule, AlertFire, TagStat, MetricHistory } from './types'

/** Carries the server's {code, message} so the UI can localize by code (e.g. TARGET_NAME_TAKEN). */
export class ApiError extends Error {
  code: string
  constructor(code: string, message: string) {
    super(message)
    this.code = code
  }
}

async function asApiError(res: Response): Promise<ApiError> {
  const body = await res.json().catch(() => null)
  return new ApiError(body?.code ?? 'ERROR', body?.message ?? `HTTP ${res.status}`)
}

export async function fetchServices(): Promise<ServiceView[]> {
  const res = await fetch('/api/services')
  if (!res.ok) throw new Error(`GET /api/services failed: ${res.status}`)
  return res.json()
}

/** Add an operator-defined HTTP liveness monitor (source=UI). Throws ApiError on 400/409. */
export async function addHttpMonitor(name: string, url: string): Promise<void> {
  const res = await fetch('/api/targets/http', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, url }),
  })
  if (!res.ok) throw await asApiError(res)
}

/** Remove a target (UI or client-registered). Static targets return 409. */
export async function removeTarget(id: string): Promise<void> {
  const res = await fetch(`/api/targets/${encodeURIComponent(id)}`, { method: 'DELETE' })
  if (!res.ok) throw await asApiError(res)
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

export async function fetchMetricHistory(id: string, key: string, range: string): Promise<MetricHistory> {
  const res = await fetch(
    `/api/services/${encodeURIComponent(id)}/metric-history?key=${encodeURIComponent(key)}&range=${encodeURIComponent(range)}`,
  )
  if (!res.ok) throw new Error(`GET metric-history ${key}/${range}: ${res.status}`)
  return res.json()
}

export async function fetchMetricBreakdown(id: string, metric: string, tag: string, filter?: string): Promise<TagStat[]> {
  const q = new URLSearchParams({ metric, tag })
  if (filter) q.set('filter', filter)
  const res = await fetch(`/api/services/${encodeURIComponent(id)}/metric-breakdown?${q.toString()}`)
  if (!res.ok) throw new Error(`GET metric-breakdown ${metric}/${tag}: ${res.status}`)
  return res.json()
}
