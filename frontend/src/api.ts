import type { ServiceView } from './types'

export async function fetchServices(): Promise<ServiceView[]> {
  const res = await fetch('/api/services')
  if (!res.ok) throw new Error(`GET /api/services failed: ${res.status}`)
  return res.json()
}
