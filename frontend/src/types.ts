export type ServiceState = 'UP' | 'DEGRADED' | 'DOWN' | 'UNKNOWN'
export type ServiceType = 'SPRING' | 'HTTP'
export type Render = 'GAUGE' | 'CHART' | 'NUMBER'
export type Unit = 'BYTES' | 'PERCENT' | 'COUNT' | 'SECONDS' | 'MILLIS' | 'TIMESTAMP'

export interface ResolvedWidget {
  key: string
  label: string
  render: Render
  unit: Unit
  value: number | null
  max: number | null
  cumulative: boolean
}

export interface ResolvedGroup {
  title: string
  widgets: ResolvedWidget[]
}

export interface ServiceView {
  id: string
  name: string
  type: ServiceType
  state: ServiceState
  tags: string[]
  latencyMs: number | null
  sparkline: (number | null)[]
}

export interface ServiceDetail {
  id: string
  name: string
  type: ServiceType
  state: ServiceState
  latencyMs: number | null
  sparkline: (number | null)[]
  groups: ResolvedGroup[]
}
