// Static, human-authored documentation per dashboard widget key: what the metric means and a
// monitoring tip (what's healthy / what to watch). `important` flags the "keep an eye on these"
// signals for visual emphasis. Keyed by the backend widget key.
export interface MetricInfo {
  description: string
  tip?: string
  important?: boolean
}

export const METRIC_INFO: Record<string, MetricInfo> = {
  // Basic
  uptime: { description: 'Time since the JVM started (process.uptime).', tip: 'A sudden reset to near-zero means the app restarted/crashed — correlate with a DOWN blip.' },
  started: { description: 'Wall-clock time the process started (process.start.time).' },
  'ready-time': { description: 'Time from JVM start until the app was ready to serve (application.ready.time).', tip: 'Watch for creep across deploys — a growing startup time hints at heavier init or slow dependencies.' },
  'cpu-process': { description: "This JVM process's CPU usage as a fraction of available cores (process.cpu.usage).", tip: 'Sustained >80% means this service is CPU-bound — scale out or profile hot paths. In a container it respects the CPU limit.' },
  'cpu-system': { description: 'Whole-host/system CPU usage (system.cpu.usage) — not just this process.', tip: 'High system CPU with low process CPU means a noisy neighbour on the host/node.' },
  'load-1m': { description: '1-minute OS load average (system.load.average.1m).', tip: 'Compare to CPU count: load ≳ cores for a sustained period = the machine is saturated / queueing.' },
  'cpu-count': { description: 'Cores the JVM sees (system.cpu.count). In a container this is the CPU limit, not the host.', tip: 'If this is the whole host (e.g. 14) you forgot a `--cpus`/limit — the JVM will size thread pools off it.' },
  'files-open': { description: 'Open file descriptors vs the OS limit (process.files.open / .max).', tip: 'Climbing toward the max = a descriptor leak (unclosed sockets/files) → "Too many open files" crashes.', important: true },
  'disk-free': { description: 'Free space on the filesystem the app runs on (disk.free / disk.total).', tip: 'In Docker Desktop this is the VM disk, not your host disk. Alert well before 0 — a full disk breaks logging and writes.', important: true },

  // Throughput & Errors (golden signals)
  rps: { description: 'HTTP requests per second (http.server.requests count, per-second rate).', tip: 'Your traffic (Google SRE golden signal). Sudden drop to 0 while UP = upstream/routing issue; spikes = load.' },
  'error-rate': { description: 'Share of recent requests that failed (4xx+5xx / total).', tip: 'The errors golden signal. A rising rate is usually the first sign of an incident — alert on it.', important: true },
  'alloc-rate': { description: 'Heap allocation rate (jvm.gc.memory.allocated, bytes/sec).', tip: 'High allocation churn drives GC frequency. A steady climb with rising GC = allocation pressure to profile.' },

  // JVM memory — heap
  'heap-used': { description: 'Live heap in use vs max (-Xmx). In a container -Xmx derives from the memory limit.', tip: 'Instantaneous heap sawtooths (fills, GC drops it) — that is normal. Watch the floor after GC, not the peaks.', important: true },
  'heap-eden': { description: 'G1 Eden space — where new objects are allocated.', tip: 'Sawtooths every minor GC; that is healthy. Its cycle rate ≈ your minor-GC frequency.' },
  'heap-old': { description: 'G1 Old Gen — long-lived objects promoted out of young gen.', tip: 'A steadily rising floor that GC never reclaims is the classic memory-leak signature.', important: true },
  'heap-survivor': { description: 'G1 Survivor space — objects that survived a minor GC, aging toward old gen.' },

  // JVM memory — non-heap
  'nonheap-used': { description: 'Non-heap memory: Metaspace, code cache, thread stacks, direct buffers (not bounded by -Xmx).', tip: 'Grows on demand — a trend, not a fill ratio. Still counts toward container RSS, so it can cause an OOM-kill even when heap is fine.' },
  'nonheap-metaspace': { description: 'Metaspace — class metadata for loaded classes.', tip: 'Should plateau. Unbounded growth = a classloader leak (common with hot-reload / repeated redeploys in one JVM).', important: true },
  'nonheap-compressed-class': { description: 'Compressed class space — a sub-region of Metaspace for class pointers.' },
  'nonheap-code-cache': { description: 'JIT-compiled native code (CodeHeap).', tip: 'If it fills, the JIT stops compiling and performance quietly degrades — watch for a plateau at the ceiling.' },
  buffers: { description: 'Direct/mapped NIO byte buffers (jvm.buffer.memory.used) — off-heap.', tip: 'Netty/NIO-heavy apps grow this; a leak here shows as rising RSS with flat heap.' },

  // GC
  'gc-pause-count': { description: 'Total GC pauses per interval (jvm.gc.pause count).', tip: 'Frequent pauses = allocation pressure. Combine with pause duration for real impact.' },
  'gc-pause-max': { description: 'Longest GC pause time.', tip: 'Long pauses = latency spikes / stop-the-world stalls. Sustained high max hurts p99.' },
  'gc-full': { description: 'Full (major) GC count — the expensive, stop-the-world collections.', tip: 'Should be rare/zero in steady state. Frequent Full GCs = severe memory pressure or a leak — investigate immediately.', important: true },
  'gc-minor': { description: 'Minor (young-gen) GC count — cheap, frequent collections.', tip: 'Normal to see many; only a concern if the rate is extreme (allocation storm).' },
  'gc-allocated': { description: 'Bytes allocated in the young gen between GCs (jvm.gc.memory.allocated).' },
  'gc-promoted': { description: 'Bytes promoted from young to old gen per GC.', tip: 'High sustained promotion feeds Old Gen growth → eventual Full GC. Watch alongside heap-old.' },
  'gc-live-data': { description: 'Long-lived heap size after the last major collection (jvm.gc.live.data.size).', tip: 'The real "working set" of the app. A rising baseline = a leak.' },
  'gc-max-data': { description: 'Max old-gen size seen after a collection.' },
  'gc-overhead': { description: 'Fraction of recent wall-clock time spent in GC (jvm.gc.overhead).', tip: 'The single best "is GC hurting me" number. >25% sustained = the app is thrashing GC — fix memory before it OOMs.', important: true },
  'heap-after-gc': { description: 'Old-gen fraction still used right after a GC (jvm.memory.usage.after.gc).', tip: 'The go-to leak/pressure signal: if this stays high (>90%) GC can’t reclaim → OutOfMemory is coming.', important: true },

  // Threads
  'threads-live': { description: 'Current live JVM threads (jvm.threads.live).', tip: 'A steady climb = a thread leak (unbounded executor / per-request threads).', important: true },
  'threads-daemon': { description: 'Daemon threads (background: GC, schedulers, pools).' },
  'threads-peak': { description: 'Peak live thread count since start.' },
  'threads-started': { description: 'Total threads ever started (cumulative).', tip: 'A fast-growing total = threads created per request/task instead of pooled.' },
  'threads-runnable': { description: 'Threads in RUNNABLE state (executing or ready).' },
  'threads-blocked': { description: 'Threads BLOCKED on a monitor lock.', tip: 'Sustained blocked threads = lock contention — a scalability bottleneck to profile.', important: true },
  'threads-waiting': { description: 'Threads WAITING (parked, e.g. idle pool threads).' },
  'threads-timed-waiting': { description: 'Threads in TIMED_WAITING (sleep/poll with a timeout).' },

  // Classes & HTTP
  'classes-loaded': { description: 'Classes currently loaded in the JVM (jvm.classes.loaded).' },
  'classes-unloaded': { description: 'Classes unloaded since start.', tip: 'Rising unloads alongside Metaspace churn hints at classloader recycling.' },
  'compilation-time': { description: 'Total JIT compilation time.' },
  'http-requests': { description: 'Total HTTP requests handled (http.server.requests, per-poll delta).', tip: 'Open the detail for a by-endpoint breakdown — see which routes carry the traffic.' },
  'http-latency-avg': { description: 'Mean server-side request latency (TOTAL_TIME / COUNT).', tip: 'Averages hide tail latency — check Max too. Rising avg = a slow dependency or GC pauses.' },
  'http-latency-max': { description: 'Max request latency in the window.', tip: 'Your worst-case / p100. Correlate spikes with GC pauses and Full GC.' },
  'http-active': { description: 'Requests currently in flight (http.server.requests.active).', tip: 'A climbing number that never drains = requests piling up faster than they complete → saturation.' },
  'http-success': { description: 'Successful (2xx) requests (outcome=SUCCESS).' },
  'http-client-error': { description: '4xx client-error responses.', tip: 'Open the detail — the by-endpoint table shows WHICH routes 4xx. A spike on one route = a bad client/contract.' },
  'http-server-error': { description: '5xx server-error responses.', tip: 'Your bugs/outages. Any sustained 5xx warrants a look; alert via Error rate.', important: true },

  // Logback
  'log-error': { description: 'ERROR-level log events per interval (logback.events).', tip: 'A burst of ERROR logs usually precedes or accompanies an incident.', important: true },
  'log-warn': { description: 'WARN-level log events per interval.' },
  'log-info': { description: 'INFO-level log events per interval.' },

  // Data sources (HikariCP) — only present with a DB pool
  'hikari-active': { description: 'Active (in-use) DB connections in the Hikari pool.' },
  'hikari-idle': { description: 'Idle DB connections available in the pool.' },
  'hikari-pending': { description: 'Threads waiting to borrow a DB connection.', tip: 'Anything >0 sustained = pool exhaustion — requests are blocked waiting for a connection. Raise pool size or fix slow queries.', important: true },

  // Web (Tomcat)
  'tomcat-sessions-active': { description: 'Active HTTP sessions.' },
  'tomcat-sessions-created': { description: 'Total sessions created.' },
  'tomcat-sessions-expired': { description: 'Sessions expired (timed out).' },
  'tomcat-sessions-rejected': { description: 'Sessions rejected because the max was reached.', tip: 'Anything >0 = you hit the session cap — users are being turned away.', important: true },
  'tomcat-threads-busy': { description: 'Busy Tomcat worker threads.', tip: 'Near the max connector threads = the web tier is saturated; new requests queue.' },

  // Scheduled tasks
  'sched-exec': { description: 'Scheduled @Scheduled task executions per interval (tasks.scheduled.execution).' },
  'sched-active': { description: 'Scheduled tasks currently running.', tip: 'A task stuck >0 for long may be hung or overlapping its own schedule.' },
}

export const infoFor = (key: string): MetricInfo | undefined => METRIC_INFO[key]
