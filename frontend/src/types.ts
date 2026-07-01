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
  higherIsBetter: boolean
  perSecond: boolean
  ratio: { numerator: string[]; denominator: string[] } | null
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

// Metric-threshold alerting (configured per metric, keyed by the dashboard widget key).
export interface AlertRule {
  key: string
  label: string
  comparator: 'GT' | 'LT'
  threshold: number // ratio 0-1 for PERCENT rules
  unit: Unit
  cooldownSeconds: number
  forSeconds: number // must breach continuously this long before firing (0 = immediate)
  enabled: boolean
}

export interface AlertFire {
  targetId: string
  targetName: string
  ruleKey: string
  label: string
  value: number
  threshold: number
  unit: Unit
  at: string
}

// Per-tag breakdown of a metric, e.g. http.server.requests by uri (endpoint).
export interface TagStat {
  value: string
  count: number | null
  mean: number | null
  max: number | null
}
