// Static metadata per dashboard widget key: importance flag and whether a monitoring tip exists.
// Description and tip TEXT live in src/i18n.ts as metric.<key>.desc / metric.<key>.tip.
export interface MetricInfo {
  important?: boolean
  hasTip?: boolean
}

export const METRIC_INFO: Record<string, MetricInfo> = {
  // Basic
  uptime:          { hasTip: true },
  started:         {},
  'ready-time':    { hasTip: true },
  'cpu-process':   { hasTip: true },
  'cpu-system':    { hasTip: true },
  'load-1m':       { hasTip: true },
  'cpu-count':     { hasTip: true },
  'files-open':    { important: true, hasTip: true },
  'disk-free':     { important: true, hasTip: true },

  // Throughput & errors (golden signals)
  rps:             { hasTip: true },
  'error-rate':    { important: true, hasTip: true },
  'alloc-rate':    { hasTip: true },

  // JVM memory — heap
  'heap-used':     { important: true, hasTip: true },
  'heap-eden':     { hasTip: true },
  'heap-old':      { important: true, hasTip: true },
  'heap-survivor': {},

  // JVM memory — non-heap
  'nonheap-used':             { hasTip: true },
  'nonheap-metaspace':        { important: true, hasTip: true },
  'nonheap-compressed-class': {},
  'nonheap-code-cache':       { hasTip: true },
  buffers:                    { hasTip: true },

  // GC
  'gc-pause-count': { hasTip: true },
  'gc-pause-max':   { hasTip: true },
  'gc-full':        { important: true, hasTip: true },
  'gc-minor':       { hasTip: true },
  'gc-allocated':   {},
  'gc-promoted':    { hasTip: true },
  'gc-live-data':   { hasTip: true },
  'gc-max-data':    {},
  'gc-overhead':    { important: true, hasTip: true },
  'heap-after-gc':  { important: true, hasTip: true },

  // Threads
  'threads-live':         { important: true, hasTip: true },
  'threads-daemon':       {},
  'threads-peak':         {},
  'threads-started':      { hasTip: true },
  'threads-runnable':     {},
  'threads-blocked':      { important: true, hasTip: true },
  'threads-waiting':      {},
  'threads-timed-waiting':{},

  // Classes & HTTP
  'classes-loaded':   {},
  'classes-unloaded': { hasTip: true },
  'compilation-time': {},
  'http-requests':    { hasTip: true },
  'http-latency-avg': { hasTip: true },
  'http-latency-max': { hasTip: true },
  'http-active':      { hasTip: true },
  'http-success':     {},
  'http-client-error':{ hasTip: true },
  'http-server-error':{ important: true, hasTip: true },

  // Logback
  'log-error': { important: true, hasTip: true },
  'log-warn':  {},
  'log-info':  {},

  // HikariCP
  'hikari-active':  {},
  'hikari-idle':    {},
  'hikari-pending': { important: true, hasTip: true },

  // Tomcat
  'tomcat-sessions-active':   {},
  'tomcat-sessions-created':  {},
  'tomcat-sessions-expired':  {},
  'tomcat-sessions-rejected': { important: true, hasTip: true },
  'tomcat-threads-busy':      { hasTip: true },

  // Scheduled tasks
  'sched-exec':   {},
  'sched-active': { hasTip: true },
}

export const infoFor = (key: string): MetricInfo | undefined => METRIC_INFO[key]
