import type { ServiceView, ServiceDetail } from './types'

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
