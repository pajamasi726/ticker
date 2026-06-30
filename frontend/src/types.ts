export type ServiceState = 'UP' | 'DEGRADED' | 'DOWN' | 'UNKNOWN'
export type ServiceType = 'SPRING' | 'HTTP'

export interface ServiceView {
  id: string
  name: string
  type: ServiceType
  state: ServiceState
  tags: string[]
  latencyMs: number | null
  sparkline: (number | null)[]
}

export interface MetricValue {
  name: string
  tag: string | null
  measurements: Record<string, number>
}

export interface ServiceDetail {
  id: string
  name: string
  type: ServiceType
  state: ServiceState
  latencyMs: number | null
  sparkline: (number | null)[]
  metrics: MetricValue[]
}
