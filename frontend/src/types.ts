export type ServiceState = 'UP' | 'DEGRADED' | 'DOWN' | 'UNKNOWN'
export type ServiceType = 'SPRING' | 'HTTP'
// How a target got here: STATIC (targets.yml, not removable), REGISTERED (client heartbeat),
// UI (operator-added HTTP monitor, removable from the wall).
export type TargetSource = 'STATIC' | 'REGISTERED' | 'UI'
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
  available: boolean // false → target doesn't expose this metric; UI shows it dimmed ("not collected")
}

export interface ResolvedGroup {
  title: string
  widgets: ResolvedWidget[]
}

export interface ServiceView {
  id: string
  name: string
  instance?: string | null // host:port for a same-named replica (registered targets); null otherwise
  ip?: string | null       // the instance's self-reported IP (registered targets only)
  type: ServiceType
  state: ServiceState
  source: TargetSource
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
  // Identity line for the header: instance (hostname:port, registered replicas only), its
  // self-reported IP, and the URL the collector polls. instance/ip are null for static/UI targets.
  instance?: string | null
  ip?: string | null
  url?: string | null
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

// Downsampled persisted history for a metric over a time range (opt-in DB feature).
export interface HistoryPoint {
  t: number // bucket start, epoch millis
  v: number // averaged value in the bucket
}

export interface MetricHistory {
  range: string
  from: number
  to: number
  bucketMs: number
  points: HistoryPoint[]
}

// ---- Admin view (storage ops + collector self-info) ----

export interface ArchiveStats { enabled: boolean; fileCount: number; totalBytes: number }

export interface HistoryStats {
  enabled: boolean
  db?: 'H2' | 'MYSQL' | 'POSTGRESQL'
  rowCount?: number
  oldestTsMillis?: number | null
  newestTsMillis?: number | null
  h2FileBytes?: number | null
  retentionMillis?: number
  sampleIntervalMillis?: number
  archive?: ArchiveStats
  backupSupported: boolean
}

export interface BackupResult { file: string; bytes: number; tookMs: number }
export interface BackupFile { name: string; bytes: number; createdAtMillis: number }

export interface SilenceView { active: boolean; until: string | null }

export interface AdminInfo {
  version: string
  uptimeMillis: number
  poll: {
    intervalMillis: number
    timeoutMillis: number
    failureThreshold: number
    degradedLatencyMs: number
    stalenessMultiplier: number
  }
  server: { basePath: string | null; publicUrlConfigured: boolean; registrationExpiryMillis: number }
  alert: { enabled: boolean; webhookConfigured: boolean }
  history: { enabled: boolean; db: 'H2' | 'MYSQL' | 'POSTGRESQL' }
}

export interface AdminTarget {
  id: string
  name: string
  type: 'SPRING' | 'HTTP'
  url: string
  source: 'STATIC' | 'REGISTERED' | 'UI'
  instance: string | null
  ip: string | null
  lastSeenMillis: number | null
  state: 'UP' | 'DEGRADED' | 'DOWN' | 'UNKNOWN'
}

/** One outbound edge from the detail view's service: this app → host. */
export interface OutboundCall {
  host: string
  count: number | null
  mean: number | null // seconds
  max: number | null // seconds
  error5xx: number | null
  targetId: string | null
  targetName: string | null
  targetState: 'UP' | 'DEGRADED' | 'DOWN' | 'UNKNOWN' | null
}

// ---- Service map (/api/graph) ----
export interface GraphNode { name: string; state: 'UP' | 'DEGRADED' | 'DOWN' | 'UNKNOWN'; external: boolean }
export interface GraphEdge {
  from: string
  to: string
  external: boolean
  count: number
  mean: number | null
  max: number | null
  error5xx: number
}
export interface ServiceGraph { nodes: GraphNode[]; edges: GraphEdge[] }
