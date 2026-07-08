import type { ServiceView, ServiceDetail, AlertRule, AlertFire, TagStat, MetricHistory, HistoryStats, BackupResult, BackupFile, SilenceView, AdminInfo, AdminTarget } from './types'

/**
 * Base path the collector is served under. Injected into index.html as `window.__TICKER_BASE__` when
 * `ticker.server.base-path` is set (e.g. "/ticker"); empty by default, so calls hit "/api/**" as before.
 */
const BASE: string = (typeof window !== 'undefined' && (window as { __TICKER_BASE__?: string }).__TICKER_BASE__) || ''

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
  const res = await fetch(`${BASE}/api/services`)
  if (!res.ok) throw new Error(`GET /api/services failed: ${res.status}`)
  return res.json()
}

/** Add an operator-defined HTTP liveness monitor (source=UI). Throws ApiError on 400/409. */
export async function addHttpMonitor(name: string, url: string): Promise<void> {
  const res = await fetch(`${BASE}/api/targets/http`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, url }),
  })
  if (!res.ok) throw await asApiError(res)
}

/** Remove a target (UI or client-registered). Static targets return 409. */
export async function removeTarget(id: string): Promise<void> {
  const res = await fetch(`${BASE}/api/targets/${encodeURIComponent(id)}`, { method: 'DELETE' })
  if (!res.ok) throw await asApiError(res)
}

export async function fetchDetail(id: string): Promise<ServiceDetail> {
  const res = await fetch(`${BASE}/api/services/${encodeURIComponent(id)}/detail`)
  if (!res.ok) throw new Error(`detail ${id}: ${res.status}`)
  return res.json()
}

export async function fetchAlertRules(): Promise<AlertRule[]> {
  const res = await fetch(`${BASE}/api/alerts/rules`)
  if (!res.ok) throw new Error(`GET /api/alerts/rules: ${res.status}`)
  return res.json()
}

export async function updateAlertRule(
  key: string,
  patch: { enabled?: boolean; threshold?: number; cooldownSeconds?: number; forSeconds?: number },
): Promise<AlertRule> {
  const res = await fetch(`${BASE}/api/alerts/rules/${encodeURIComponent(key)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(patch),
  })
  if (!res.ok) throw new Error(`PUT /api/alerts/rules/${key}: ${res.status}`)
  return res.json()
}

export async function fetchRecentAlerts(): Promise<AlertFire[]> {
  const res = await fetch(`${BASE}/api/alerts/recent`)
  if (!res.ok) throw new Error(`GET /api/alerts/recent: ${res.status}`)
  return res.json()
}

export async function fetchMetricHistory(id: string, key: string, range: string): Promise<MetricHistory> {
  const res = await fetch(
    `${BASE}/api/services/${encodeURIComponent(id)}/metric-history?key=${encodeURIComponent(key)}&range=${encodeURIComponent(range)}`,
  )
  if (!res.ok) throw new Error(`GET metric-history ${key}/${range}: ${res.status}`)
  return res.json()
}

export async function fetchMetricBreakdown(id: string, metric: string, tag: string, filter?: string): Promise<TagStat[]> {
  const q = new URLSearchParams({ metric, tag })
  if (filter) q.set('filter', filter)
  const res = await fetch(`${BASE}/api/services/${encodeURIComponent(id)}/metric-breakdown?${q.toString()}`)
  if (!res.ok) throw new Error(`GET metric-breakdown ${metric}/${tag}: ${res.status}`)
  return res.json()
}

// ---- Admin: storage ops + collector self-info ----

export async function fetchHistoryStats(): Promise<HistoryStats> {
  const res = await fetch(`${BASE}/api/history/stats`)
  if (!res.ok) throw new Error(`GET /api/history/stats: ${res.status}`)
  return res.json()
}

/** Zero-downtime H2 backup. Throws ApiError with the server's code (UNSUPPORTED_DB, BACKUP_IN_PROGRESS…). */
export async function triggerBackup(): Promise<BackupResult> {
  const res = await fetch(`${BASE}/api/history/backup`, { method: 'POST' })
  if (!res.ok) throw await asApiError(res)
  return res.json()
}

export async function fetchBackups(): Promise<BackupFile[]> {
  const res = await fetch(`${BASE}/api/history/backups`)
  if (!res.ok) throw new Error(`GET /api/history/backups: ${res.status}`)
  return res.json()
}

export function backupDownloadUrl(name: string): string {
  return `${BASE}/api/history/backups/${encodeURIComponent(name)}`
}

export async function fetchSilence(): Promise<SilenceView> {
  const res = await fetch(`${BASE}/api/alerts/silence`)
  if (!res.ok) throw new Error(`GET /api/alerts/silence: ${res.status}`)
  return res.json()
}

export async function startSilence(minutes: number): Promise<SilenceView> {
  const res = await fetch(`${BASE}/api/alerts/silence`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ minutes }),
  })
  if (!res.ok) throw await asApiError(res)
  return res.json()
}

export async function clearSilence(): Promise<SilenceView> {
  const res = await fetch(`${BASE}/api/alerts/silence`, { method: 'DELETE' })
  if (!res.ok) throw new Error(`DELETE /api/alerts/silence: ${res.status}`)
  return res.json()
}

export async function fetchAdminInfo(): Promise<AdminInfo> {
  const res = await fetch(`${BASE}/api/admin/info`)
  if (!res.ok) throw new Error(`GET /api/admin/info: ${res.status}`)
  return res.json()
}

export async function fetchAdminTargets(): Promise<AdminTarget[]> {
  const res = await fetch(`${BASE}/api/admin/targets`)
  if (!res.ok) throw new Error(`GET /api/admin/targets: ${res.status}`)
  return res.json()
}
