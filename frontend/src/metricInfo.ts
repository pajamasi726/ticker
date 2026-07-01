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
  'heap-survivor':  {},
  'heap-committed': { hasTip: true },

  // JVM memory — non-heap
  'nonheap-used':             { hasTip: true },
  'nonheap-metaspace':        { important: true, hasTip: true },
  'nonheap-compressed-class': {},
  'nonheap-code-cache':       { hasTip: true },
  'nonheap-committed':        { hasTip: true },
  buffers:                    { hasTip: true },
  'buffer-count':             { hasTip: true },

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

  // HTTP client
  'http-client-rps':          { hasTip: true },
  'http-client-latency-avg':  { hasTip: true },
  'http-client-latency-max':  { hasTip: true },
  'http-client-server-error': { important: true, hasTip: true },

  // Logback
  'log-error': { important: true, hasTip: true },
  'log-warn':  {},
  'log-info':  {},

  // HikariCP
  'hikari-active':  {},
  'hikari-idle':    {},
  'hikari-pending': { important: true, hasTip: true },
  'hikari-total':   {},
  'hikari-max':     {},

  // JDBC
  'jdbc-active':    { important: true, hasTip: true },
  'jdbc-idle':      {},
  'jdbc-max':       {},
  'jdbc-min':       {},

  // Hibernate
  'hibernate-sessions-open':          { hasTip: true },
  'hibernate-transactions':           { hasTip: true },
  'hibernate-connections-obtained':   { hasTip: true },
  'hibernate-statements':             { hasTip: true },
  'hibernate-query-executions':       { hasTip: true },
  'hibernate-query-max':              { hasTip: true },
  'hibernate-2lc-hit':                { hasTip: true },
  'hibernate-2lc-miss':               { important: true, hasTip: true },
  'hibernate-flushes':                {},

  // Spring Cache
  'cache-gets-hit':  { hasTip: true },
  'cache-gets-miss': { important: true, hasTip: true },
  'cache-puts':      {},
  'cache-evictions': { hasTip: true },
  'cache-size':      {},

  // Tomcat
  'tomcat-sessions-active':     {},
  'tomcat-sessions-created':    {},
  'tomcat-sessions-expired':    {},
  'tomcat-sessions-rejected':   { important: true, hasTip: true },
  'tomcat-threads-busy':        { hasTip: true },
  'tomcat-threads-current':     { hasTip: true },
  'tomcat-threads-max':         {},
  'tomcat-connections-current': { important: true, hasTip: true },
  'tomcat-connections-max':     {},
  'tomcat-global-requests':     { hasTip: true },
  'tomcat-global-request-avg':  { hasTip: true },
  'tomcat-global-errors':       { important: true, hasTip: true },
  'tomcat-bytes-sent':          { hasTip: true },
  'tomcat-bytes-received':      { hasTip: true },
  'tomcat-sessions-active-max': {},

  // Scheduled tasks
  'sched-exec':   {},
  'sched-active': { hasTip: true },

  // Task executor (@Async / ThreadPoolTaskExecutor)
  'executor-active':    { hasTip: true },
  'executor-queued':    { important: true, hasTip: true },
  'executor-pool-size': {},
  'executor-completed': { hasTip: true },
}

export const infoFor = (key: string): MetricInfo | undefined => METRIC_INFO[key]
