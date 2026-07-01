import { useSyncExternalStore } from 'react'

export type Lang = 'en' | 'ko'
export const LANGS: Lang[] = ['en', 'ko']

const KEY = 'ticker.lang'
const isLang = (v: unknown): v is Lang => v === 'en' || v === 'ko'

function defaultLang(): Lang {
  try {
    const saved = localStorage.getItem(KEY)
    if (isLang(saved)) return saved
  } catch { /* localStorage unavailable */ }
  try {
    if (navigator.language.startsWith('ko')) return 'ko'
  } catch { /* navigator unavailable */ }
  return 'en'
}

let current: Lang = defaultLang()

const listeners = new Set<() => void>()

export function setLang(l: Lang) {
  current = l
  try { localStorage.setItem(KEY, l) } catch { /* ignore */ }
  listeners.forEach((cb) => cb())
}

/** React hook: current language, re-renders on change. */
export function useLang(): Lang {
  return useSyncExternalStore(
    (l) => { listeners.add(l); return () => { listeners.delete(l) } },
    () => current,
  )
}

// ─── Dictionaries ────────────────────────────────────────────────────────────

type Dict = Record<string, string>

const en: Dict = {
  // App shell
  'app.sub': 'service liveness',
  'banner.title': 'Collector unreachable.',
  'banner.stale': 'Last good update {n}s ago — the status below may be stale.',
  'banner.noUpdate': 'No successful update yet — service status cannot be confirmed.',
  'banner.unreachableA': 'Cannot reach the collector at',
  'banner.unreachableB': '. Confirm it is running — and that an external probe (k8s liveness + an outside ping) is watching it.',

  // Summary bar state labels
  'summary.up': 'up',
  'summary.degraded': 'degraded',
  'summary.down': 'down',
  'summary.unknown': 'unknown',

  // Status wall
  'wall.empty': "No services yet — point an app's ticker.client.collector-url here.",

  // Add HTTP monitor + remove
  'addmon.add': '+ Add HTTP monitor',
  'addmon.name': 'name',
  'addmon.url': 'https://host/health',
  'addmon.submit': 'Add',
  'addmon.cancel': 'Cancel',
  'addmon.hint': 'Polled like any target — GET, a 2xx response means up.',
  'addmon.errTaken': 'That name is already in use.',
  'addmon.errInvalid': 'Enter a name and a http(s):// URL.',
  'addmon.errGeneric': 'Could not add the monitor.',
  'tile.remove': 'Remove monitor',
  'tile.removeConfirm': 'Remove monitor "{name}"?',

  // Service detail panel
  'detail.allServices': '← all services',
  'detail.allServicesAria': 'Back to all services',
  'detail.backAria': 'Back to dashboard',
  'detail.staleError': 'Lost connection to the collector — details may be stale.',
  'detail.recentAlerts': '⚠ Recent alerts',
  'detail.httpNote': 'HTTP target — no JVM metrics. Latency {n} ms.',
  'detail.noMetrics': 'No metrics — unreachable or actuator metrics not exposed.',
  'detail.uptime': 'up {v}',
  'legend.important': 'Key signal — watch these first when diagnosing',

  // Metric inspector — trend panel
  'trend.live': 'live · last {n} samples',
  'trend.liveDelta': 'live · last {n} samples (per-poll delta)',
  'trend.historical': '{range} · {n} points',
  'scale.auto': 'auto',
  'scale.fullPercent': '0–100%',
  'scale.fullMax': '0–max',
  'stats.min': 'min',
  'stats.avg': 'avg',
  'stats.max': 'max',
  'hist.none': 'No history yet for {range} — the recorder is still filling it in.',
  'collecting': 'Collecting samples… the trend appears after a few polls.',

  // Breakdown table
  'breakdown.requests': 'Requests by endpoint',
  'breakdown.clientErrors': 'Client errors by endpoint',
  'breakdown.latency': 'Latency by endpoint',
  'breakdown.successRequests': 'Successful requests by endpoint',
  'breakdown.serverErrors': 'Server errors by endpoint',
  'breakdown.totals': '· totals since start',
  'table.endpoint': 'endpoint',
  'table.count': 'count',
  'table.avg': 'avg',
  'table.max': 'max',
  'loading': 'loading…',
  'noData': 'no data',

  // Metric info panel
  'info.about': 'ⓘ About this metric',
  'info.tip': 'Monitoring tip',
  'metric.unavailable': 'Not collected on this target',

  // Alert section
  'alert.title': '🔔 Alert',
  'alert.none': 'No alert available for this metric.',
  'alert.currently': 'currently',
  'status.ok': 'OK',
  'status.breaching': 'BREACHING',
  'status.disabled': 'disabled',
  'alert.enabled': 'Enabled',
  'alert.threshold': 'Threshold — alert when {cmp} {threshold}{unit}',
  'alert.for': 'For — must stay breaching this long before firing',
  'alert.cooldown': 'Cooldown — wait between repeat alerts',
  'alert.forUnit': 'seconds (0 = immediate)',
  'alert.cooldownUnit': 'seconds',
  'alert.forCond': 'for {n}s',
  'alert.recentFires': 'Recent fires',
  'alert.saved': 'saved ✓',
  'alert.save': 'Save',

  // ── Metric descriptions and tips ──────────────────────────────────────────

  // Basic
  'metric.uptime.desc': 'Time since the JVM started (process.uptime).',
  'metric.uptime.tip': 'A sudden reset to near-zero means the app restarted/crashed — correlate with a DOWN blip.',
  'metric.started.desc': 'Wall-clock time the process started (process.start.time).',
  'metric.ready-time.desc': 'Time from JVM start until the app was ready to serve (application.ready.time).',
  'metric.ready-time.tip': 'Watch for creep across deploys — a growing startup time hints at heavier init or slow dependencies.',
  'metric.cpu-process.desc': "This JVM process's CPU usage as a fraction of available cores (process.cpu.usage).",
  'metric.cpu-process.tip': 'Sustained >80% means this service is CPU-bound — scale out or profile hot paths. In a container it respects the CPU limit.',
  'metric.cpu-system.desc': 'Whole-host/system CPU usage (system.cpu.usage) — not just this process.',
  'metric.cpu-system.tip': 'High system CPU with low process CPU means a noisy neighbour on the host/node.',
  'metric.load-1m.desc': '1-minute OS load average (system.load.average.1m).',
  'metric.load-1m.tip': 'Compare to CPU count: load ≳ cores for a sustained period = the machine is saturated / queueing.',
  'metric.cpu-count.desc': 'Cores the JVM sees (system.cpu.count). In a container this is the CPU limit, not the host.',
  'metric.cpu-count.tip': 'If this is the whole host (e.g. 14) you forgot a `--cpus`/limit — the JVM will size thread pools off it.',
  'metric.files-open.desc': 'Open file descriptors vs the OS limit (process.files.open / .max).',
  'metric.files-open.tip': 'Climbing toward the max = a descriptor leak (unclosed sockets/files) → "Too many open files" crashes.',
  'metric.disk-free.desc': 'Free space on the filesystem the app runs on (disk.free / disk.total).',
  'metric.disk-free.tip': 'In Docker Desktop this is the VM disk, not your host disk. Alert well before 0 — a full disk breaks logging and writes.',

  // Throughput & errors (golden signals)
  'metric.rps.desc': 'HTTP requests per second (http.server.requests count, per-second rate).',
  'metric.rps.tip': 'Your traffic (Google SRE golden signal). Sudden drop to 0 while UP = upstream/routing issue; spikes = load.',
  'metric.error-rate.desc': 'Share of recent requests that failed (4xx+5xx / total).',
  'metric.error-rate.tip': 'The errors golden signal. A rising rate is usually the first sign of an incident — alert on it.',
  'metric.alloc-rate.desc': 'Heap allocation rate (jvm.gc.memory.allocated, bytes/sec).',
  'metric.alloc-rate.tip': 'High allocation churn drives GC frequency. A steady climb with rising GC = allocation pressure to profile.',

  // JVM memory — heap
  'metric.heap-used.desc': 'Live heap in use vs max (-Xmx). In a container -Xmx derives from the memory limit.',
  'metric.heap-used.tip': 'Instantaneous heap sawtooths (fills, GC drops it) — that is normal. Watch the floor after GC, not the peaks.',
  'metric.heap-eden.desc': 'G1 Eden space — where new objects are allocated.',
  'metric.heap-eden.tip': 'Sawtooths every minor GC; that is healthy. Its cycle rate ≈ your minor-GC frequency.',
  'metric.heap-old.desc': 'G1 Old Gen — long-lived objects promoted out of young gen.',
  'metric.heap-old.tip': 'A steadily rising floor that GC never reclaims is the classic memory-leak signature.',
  'metric.heap-survivor.desc': 'G1 Survivor space — objects that survived a minor GC, aging toward old gen.',
  'metric.heap-committed.desc': 'Heap memory committed (reserved) from the OS — always ≥ used (jvm.memory.committed, area=heap).',
  'metric.heap-committed.tip': 'A large gap between committed and used is fine; the JVM pre-reserved more than it currently needs. Watch if committed keeps growing after GC.',

  // JVM memory — non-heap
  'metric.nonheap-used.desc': 'Non-heap memory: Metaspace, code cache, thread stacks, direct buffers (not bounded by -Xmx).',
  'metric.nonheap-used.tip': 'Grows on demand — a trend, not a fill ratio. Still counts toward container RSS, so it can cause an OOM-kill even when heap is fine.',
  'metric.nonheap-metaspace.desc': 'Metaspace — class metadata for loaded classes.',
  'metric.nonheap-metaspace.tip': 'Should plateau. Unbounded growth = a classloader leak (common with hot-reload / repeated redeploys in one JVM).',
  'metric.nonheap-compressed-class.desc': 'Compressed class space — a sub-region of Metaspace for class pointers.',
  'metric.nonheap-code-cache.desc': 'JIT-compiled native code (CodeHeap).',
  'metric.nonheap-code-cache.tip': 'If it fills, the JIT stops compiling and performance quietly degrades — watch for a plateau at the ceiling.',
  'metric.nonheap-committed.desc': 'Non-heap memory committed from the OS — Metaspace, code cache, etc. (jvm.memory.committed, area=nonheap).',
  'metric.nonheap-committed.tip': 'Grows as new classes load and the JIT warms up. A plateau is healthy; continuous growth hints at classloader churn.',
  'metric.buffers.desc': 'Direct/mapped NIO byte buffers (jvm.buffer.memory.used) — off-heap.',
  'metric.buffers.tip': 'Netty/NIO-heavy apps grow this; a leak here shows as rising RSS with flat heap.',
  'metric.buffer-count.desc': 'Number of direct NIO byte buffers currently allocated (jvm.buffer.count, id=direct).',
  'metric.buffer-count.tip': 'A rising count with flat capacity = many small buffers created but not released. Correlate with RSS growth.',

  // GC
  'metric.gc-pause-count.desc': 'Total GC pauses per interval (jvm.gc.pause count).',
  'metric.gc-pause-count.tip': 'Frequent pauses = allocation pressure. Combine with pause duration for real impact.',
  'metric.gc-pause-max.desc': 'Longest GC pause time.',
  'metric.gc-pause-max.tip': 'Long pauses = latency spikes / stop-the-world stalls. Sustained high max hurts p99.',
  'metric.gc-full.desc': 'Full (major) GC count — the expensive, stop-the-world collections.',
  'metric.gc-full.tip': 'Should be rare/zero in steady state. Frequent Full GCs = severe memory pressure or a leak — investigate immediately.',
  'metric.gc-minor.desc': 'Minor (young-gen) GC count — cheap, frequent collections.',
  'metric.gc-minor.tip': 'Normal to see many; only a concern if the rate is extreme (allocation storm).',
  'metric.gc-allocated.desc': 'Bytes allocated in the young gen between GCs (jvm.gc.memory.allocated).',
  'metric.gc-promoted.desc': 'Bytes promoted from young to old gen per GC.',
  'metric.gc-promoted.tip': 'High sustained promotion feeds Old Gen growth → eventual Full GC. Watch alongside heap-old.',
  'metric.gc-live-data.desc': 'Long-lived heap size after the last major collection (jvm.gc.live.data.size).',
  'metric.gc-live-data.tip': 'The real "working set" of the app. A rising baseline = a leak.',
  'metric.gc-max-data.desc': 'Max old-gen size seen after a collection.',
  'metric.gc-overhead.desc': 'Fraction of recent wall-clock time spent in GC (jvm.gc.overhead).',
  'metric.gc-overhead.tip': 'The single best "is GC hurting me" number. >25% sustained = the app is thrashing GC — fix memory before it OOMs.',
  'metric.heap-after-gc.desc': 'Old-gen fraction still used right after a GC (jvm.memory.usage.after.gc).',
  'metric.heap-after-gc.tip': "The go-to leak/pressure signal: if this stays high (>90%) GC can't reclaim → OutOfMemory is coming.",

  // Threads
  'metric.threads-live.desc': 'Current live JVM threads (jvm.threads.live).',
  'metric.threads-live.tip': 'A steady climb = a thread leak (unbounded executor / per-request threads).',
  'metric.threads-daemon.desc': 'Daemon threads (background: GC, schedulers, pools).',
  'metric.threads-peak.desc': 'Peak live thread count since start.',
  'metric.threads-started.desc': 'Total threads ever started (cumulative).',
  'metric.threads-started.tip': 'A fast-growing total = threads created per request/task instead of pooled.',
  'metric.threads-runnable.desc': 'Threads in RUNNABLE state (executing or ready).',
  'metric.threads-blocked.desc': 'Threads BLOCKED on a monitor lock.',
  'metric.threads-blocked.tip': 'Sustained blocked threads = lock contention — a scalability bottleneck to profile.',
  'metric.threads-waiting.desc': 'Threads WAITING (parked, e.g. idle pool threads).',
  'metric.threads-timed-waiting.desc': 'Threads in TIMED_WAITING (sleep/poll with a timeout).',

  // Classes & HTTP
  'metric.classes-loaded.desc': 'Classes currently loaded in the JVM (jvm.classes.loaded).',
  'metric.classes-unloaded.desc': 'Classes unloaded since start.',
  'metric.classes-unloaded.tip': 'Rising unloads alongside Metaspace churn hints at classloader recycling.',
  'metric.compilation-time.desc': 'Total JIT compilation time.',
  'metric.http-requests.desc': 'Total HTTP requests handled (http.server.requests, per-poll delta).',
  'metric.http-requests.tip': 'Open the detail for a by-endpoint breakdown — see which routes carry the traffic.',
  'metric.http-latency-avg.desc': 'Mean server-side request latency (TOTAL_TIME / COUNT).',
  'metric.http-latency-avg.tip': 'Averages hide tail latency — check Max too. Rising avg = a slow dependency or GC pauses.',
  'metric.http-latency-max.desc': 'Max request latency in the window.',
  'metric.http-latency-max.tip': 'Your worst-case / p100. Correlate spikes with GC pauses and Full GC.',
  'metric.http-active.desc': 'Requests currently in flight (http.server.requests.active).',
  'metric.http-active.tip': 'A climbing number that never drains = requests piling up faster than they complete → saturation.',
  'metric.http-success.desc': 'Successful (2xx) requests (outcome=SUCCESS).',
  'metric.http-client-error.desc': '4xx client-error responses.',
  'metric.http-client-error.tip': 'Open the detail — the by-endpoint table shows WHICH routes 4xx. A spike on one route = a bad client/contract.',
  'metric.http-server-error.desc': '5xx server-error responses.',
  'metric.http-server-error.tip': 'Your bugs/outages. Any sustained 5xx warrants a look; alert via Error rate.',

  // HTTP client
  'metric.http-client-rps.desc': 'Outbound HTTP client request rate (http.client.requests count/s).',
  'metric.http-client-rps.tip': 'Shows how heavily this service calls downstream APIs. A drop while inbound traffic is steady hints at a circuit-breaker open or client timeout.',
  'metric.http-client-latency-avg.desc': 'Mean latency of outbound HTTP client calls (http.client.requests MEAN).',
  'metric.http-client-latency-avg.tip': 'Rising mean = a downstream dependency is slowing down. Combine with max to gauge tail impact.',
  'metric.http-client-latency-max.desc': 'Worst (maximum) latency of outbound HTTP client calls in the window (http.client.requests MAX).',
  'metric.http-client-latency-max.tip': 'Latency spikes here with flat server-side latency point to a slow upstream dependency, not this service.',
  'metric.http-client-server-error.desc': '5xx responses received from downstream dependencies (http.client.requests outcome=SERVER_ERROR).',
  'metric.http-client-server-error.tip': 'Your dependency health signal. Any sustained 5xx from a downstream = that service has a problem; expect cascading failures on critical paths.',

  // Logback
  'metric.log-error.desc': 'ERROR-level log events per interval (logback.events).',
  'metric.log-error.tip': 'A burst of ERROR logs usually precedes or accompanies an incident.',
  'metric.log-warn.desc': 'WARN-level log events per interval.',
  'metric.log-info.desc': 'INFO-level log events per interval.',

  // HikariCP
  'metric.hikari-active.desc': 'Active (in-use) DB connections in the Hikari pool.',
  'metric.hikari-idle.desc': 'Idle DB connections available in the pool.',
  'metric.hikari-pending.desc': 'Threads waiting to borrow a DB connection.',
  'metric.hikari-pending.tip': 'Anything >0 sustained = pool exhaustion — requests are blocked waiting for a connection. Raise pool size or fix slow queries.',
  'metric.hikari-total.desc': 'Total connections in the HikariCP pool (hikaricp.connections) — active + idle.',
  'metric.hikari-max.desc': 'Configured maximum pool size for HikariCP (hikaricp.connections.max).',

  // JDBC
  'metric.jdbc-active.desc': 'JDBC connections currently in use (jdbc.connections.active) — generic Spring pool metric.',
  'metric.jdbc-active.tip': 'Near jdbc-max = the pool is about to exhaust. Requests will block waiting for a connection — check for slow queries holding connections.',
  'metric.jdbc-idle.desc': 'Idle pooled JDBC connections available for checkout (jdbc.connections.idle).',
  'metric.jdbc-max.desc': 'Configured maximum pool size for the generic Spring JDBC pool (jdbc.connections.max).',
  'metric.jdbc-min.desc': 'Configured minimum idle connections for the JDBC pool (jdbc.connections.min).',

  // Hibernate
  'metric.hibernate-sessions-open.desc': 'Hibernate sessions opened per second (hibernate.sessions.open count/s).',
  'metric.hibernate-sessions-open.tip': 'High rate = many short-lived sessions. Each involves a transaction; rising rate under steady load hints at a session-per-request pattern.',
  'metric.hibernate-transactions.desc': 'Database transactions executed by Hibernate (hibernate.transactions).',
  'metric.hibernate-transactions.tip': 'A transaction count far exceeding request count = multiple transactions per request — review @Transactional boundaries.',
  'metric.hibernate-connections-obtained.desc': 'JDBC connections obtained by Hibernate from the pool (hibernate.connections.obtained).',
  'metric.hibernate-connections-obtained.tip': 'Closely tracks transactions. If far higher than transactions, check for connection leaks or over-eager connection acquisition.',
  'metric.hibernate-statements.desc': 'JDBC statements prepared by Hibernate (hibernate.statements, status=prepared).',
  'metric.hibernate-statements.tip': 'A high per-request count is the N+1 query smell — enable Hibernate SQL logging or a query inspector to find the culprit.',
  'metric.hibernate-query-executions.desc': 'Total queries executed by Hibernate (hibernate.query.executions).',
  'metric.hibernate-query-executions.tip': 'Tracks query volume. Compare with transactions to spot queries-per-transaction ratios above expected.',
  'metric.hibernate-query-max.desc': 'Slowest query execution time observed (hibernate.query.executions MAX).',
  'metric.hibernate-query-max.tip': 'The worst single query. A large max with normal avg = an occasional rogue query; find it in slow-query logs.',
  'metric.hibernate-2lc-hit.desc': 'Second-level cache hits (hibernate.second.level.cache.requests, result=hit).',
  'metric.hibernate-2lc-hit.tip': 'Higher is better. Low hits alongside high misses = the L2 cache is undersized or the entity fetch pattern defeats it.',
  'metric.hibernate-2lc-miss.desc': 'Second-level cache misses (hibernate.second.level.cache.requests, result=miss).',
  'metric.hibernate-2lc-miss.tip': 'A high miss ratio (miss / (hit + miss)) means the L2 cache is not effective — review cache region sizes and eviction policies.',
  'metric.hibernate-flushes.desc': 'Hibernate session flushes — when the persistence context is synced to the DB (hibernate.flushes).',

  // Spring Cache
  'metric.cache-gets-hit.desc': 'Spring cache hits — requests served from cache (cache.gets, result=hit).',
  'metric.cache-gets-hit.tip': 'A high hit rate is the goal. Compare hits vs misses to measure cache effectiveness.',
  'metric.cache-gets-miss.desc': 'Spring cache misses — lookups that fell through to the source (cache.gets, result=miss).',
  'metric.cache-gets-miss.tip': 'A rising or dominant miss rate erodes the cache benefit and adds load to the backing store. Check TTL, eviction policy, and key cardinality.',
  'metric.cache-puts.desc': 'Entries written into the Spring cache (cache.puts).',
  'metric.cache-evictions.desc': 'Entries evicted from the Spring cache (cache.evictions).',
  'metric.cache-evictions.tip': 'Heavy eviction = the cache is too small for the working set. Increase the max size or review TTL settings.',
  'metric.cache-size.desc': 'Estimated number of entries currently held in the Spring cache (cache.size).',

  // Tomcat
  'metric.tomcat-sessions-active.desc': 'Active HTTP sessions.',
  'metric.tomcat-sessions-created.desc': 'Total sessions created.',
  'metric.tomcat-sessions-expired.desc': 'Sessions expired (timed out).',
  'metric.tomcat-sessions-rejected.desc': 'Sessions rejected because the max was reached.',
  'metric.tomcat-sessions-rejected.tip': 'Anything >0 = you hit the session cap — users are being turned away.',
  'metric.tomcat-threads-busy.desc': 'Busy Tomcat worker threads.',
  'metric.tomcat-threads-busy.tip': 'Near the max connector threads = the web tier is saturated; new requests queue.',
  'metric.tomcat-threads-current.desc': 'Current number of Tomcat worker threads (tomcat.threads.current).',
  'metric.tomcat-threads-current.tip': 'Compare with tomcat-threads-max: near the maximum means the thread pool is fully utilized and new requests will queue.',
  'metric.tomcat-threads-max.desc': 'Configured maximum Tomcat worker threads (tomcat.threads.config.max).',
  'metric.tomcat-connections-current.desc': 'Open connections currently held by Tomcat (tomcat.connections.current).',
  'metric.tomcat-connections-current.tip': 'Approaching tomcat-connections-max = the web tier is saturating. Clients will start receiving connection-refused errors.',
  'metric.tomcat-connections-max.desc': 'Maximum simultaneous connections Tomcat will accept (tomcat.connections.max).',
  'metric.tomcat-global-requests.desc': 'Total request throughput at the Tomcat connector (tomcat.global.request count/s).',
  'metric.tomcat-global-requests.tip': 'The raw connector-level request rate. Compare with http.server.requests to detect internal routing discrepancies.',
  'metric.tomcat-global-request-max.desc': 'Slowest request seen at the Tomcat connector (tomcat.global.request MAX).',
  'metric.tomcat-global-request-max.tip': 'Connector-level worst-case latency. Correlate spikes with GC pause events or downstream timeouts.',
  'metric.tomcat-global-errors.desc': 'Requests counted as errors at the Tomcat connector level (tomcat.global.error).',
  'metric.tomcat-global-errors.tip': 'Connector-level errors include network-abort and early-disconnect cases that never reach application code. Any sustained count warrants investigation.',
  'metric.tomcat-bytes-sent.desc': 'Bytes sent by the Tomcat connector per second (tomcat.global.sent bytes/s).',
  'metric.tomcat-bytes-sent.tip': 'A sudden spike = large response payloads or a high-bandwidth endpoint. Useful for diagnosing egress saturation.',
  'metric.tomcat-bytes-received.desc': 'Bytes received by the Tomcat connector per second (tomcat.global.received bytes/s).',
  'metric.tomcat-bytes-received.tip': 'High inbound traffic with normal RPS = large request bodies (file uploads, bulk payloads).',
  'metric.tomcat-sessions-active-max.desc': 'Peak concurrent active HTTP sessions observed (tomcat.sessions.active.max).',

  // Scheduled tasks
  'metric.sched-exec.desc': 'Scheduled @Scheduled task executions per interval (tasks.scheduled.execution).',
  'metric.sched-active.desc': 'Scheduled tasks currently running.',
  'metric.sched-active.tip': 'A task stuck >0 for long may be hung or overlapping its own schedule.',

  // Task executor (@Async / ThreadPoolTaskExecutor)
  'metric.executor-active.desc': 'Threads currently executing tasks in the Spring task executor pool (executor.active).',
  'metric.executor-active.tip': 'Sustained near pool size means the executor is always busy. Combine with executor-queued to see if work is also backing up.',
  'metric.executor-queued.desc': 'Tasks waiting in the executor queue for a free thread (executor.queued).',
  'metric.executor-queued.tip': 'Any persistent queue depth = the pool cannot keep up with submitted work. Increase pool size or reduce task duration.',
  'metric.executor-pool-size.desc': 'Current number of threads in the Spring task executor pool (executor.pool.size).',
  'metric.executor-completed.desc': 'Total tasks completed by the executor (executor.completed).',
  'metric.executor-completed.tip': 'The cumulative counter. A rate derived from this metric reveals executor throughput — compare with active to assess utilization.',
}

const ko: Dict = {
  // App shell
  'app.sub': '서비스 상태 모니터',
  'banner.title': '수집기에 연결할 수 없습니다.',
  'banner.stale': '{n}초 전 마지막 정상 응답 — 아래 상태가 오래되었을 수 있습니다.',
  'banner.noUpdate': '아직 정상 응답 없음 — 서비스 상태를 확인할 수 없습니다.',
  'banner.unreachableA': '수집기(',
  'banner.unreachableB': ')에 연결할 수 없습니다. 수집기가 실행 중인지, 외부 프로브(k8s liveness + 외부 핑)가 감시하는지 확인하세요.',

  // Summary bar state labels
  'summary.up': '정상',
  'summary.degraded': '저하',
  'summary.down': '다운',
  'summary.unknown': '알 수 없음',

  // Status wall
  'wall.empty': "등록된 서비스 없음 — 앱의 ticker.client.collector-url을 이 수집기로 설정하세요.",

  // Add HTTP monitor + remove
  'addmon.add': '+ HTTP 모니터 추가',
  'addmon.name': '이름',
  'addmon.url': 'https://host/health',
  'addmon.submit': '추가',
  'addmon.cancel': '취소',
  'addmon.hint': '다른 대상처럼 폴링됩니다 — GET 요청에 2xx 응답이면 정상.',
  'addmon.errTaken': '이미 사용 중인 이름입니다.',
  'addmon.errInvalid': '이름과 http(s):// URL을 입력하세요.',
  'addmon.errGeneric': '모니터를 추가할 수 없습니다.',
  'tile.remove': '모니터 삭제',
  'tile.removeConfirm': '"{name}" 모니터를 삭제할까요?',

  // Service detail panel
  'detail.allServices': '← 전체 서비스',
  'detail.allServicesAria': '전체 서비스로 돌아가기',
  'detail.backAria': '대시보드로 돌아가기',
  'detail.staleError': '수집기 연결이 끊겼습니다 — 상세 정보가 오래되었을 수 있습니다.',
  'detail.recentAlerts': '⚠ 최근 알림',
  'detail.httpNote': 'HTTP 대상 — JVM 메트릭 없음. 지연 시간 {n} ms.',
  'detail.noMetrics': '메트릭 없음 — 연결 불가 또는 actuator 메트릭이 노출되지 않음.',
  'detail.uptime': '가동 {v}',
  'legend.important': '핵심 지표 — 장애 진단 시 먼저 확인하세요',

  // Metric inspector — trend panel
  'trend.live': '실시간 · 최근 {n} 샘플',
  'trend.liveDelta': '실시간 · 최근 {n} 샘플 (폴링 델타)',
  'trend.historical': '{range} · {n} 포인트',
  'scale.auto': '자동',
  'scale.fullPercent': '0–100%',
  'scale.fullMax': '0–max',
  'stats.min': '최솟값',
  'stats.avg': '평균',
  'stats.max': '최댓값',
  'hist.none': '{range} 기간 이력 없음 — 기록기가 아직 수집 중입니다.',
  'collecting': '샘플 수집 중… 몇 번의 폴링 후 추이가 표시됩니다.',

  // Breakdown table
  'breakdown.requests': '엔드포인트별 요청',
  'breakdown.clientErrors': '엔드포인트별 클라이언트 오류',
  'breakdown.latency': '엔드포인트별 지연 시간',
  'breakdown.successRequests': '엔드포인트별 성공 요청',
  'breakdown.serverErrors': '엔드포인트별 서버 오류',
  'breakdown.totals': '· 시작 이후 합계',
  'table.endpoint': '엔드포인트',
  'table.count': '횟수',
  'table.avg': '평균',
  'table.max': '최대',
  'loading': '불러오는 중…',
  'noData': '데이터 없음',

  // Metric info panel
  'info.about': 'ⓘ 이 메트릭 정보',
  'info.tip': '모니터링 팁',
  'metric.unavailable': '이 대상에서 수집 안 됨',

  // Alert section
  'alert.title': '🔔 알림',
  'alert.none': '이 메트릭에는 알림을 설정할 수 없습니다.',
  'alert.currently': '현재',
  'status.ok': '정상',
  'status.breaching': '임계값 초과',
  'status.disabled': '비활성',
  'alert.enabled': '활성화',
  'alert.threshold': '임계값 — {cmp} {threshold}{unit} 일 때 알림',
  'alert.for': '지속 — 알림 발송 전 임계값 초과 유지 시간',
  'alert.cooldown': '재알림 대기 — 반복 알림 사이 대기 시간',
  'alert.forUnit': '초 (0 = 즉시)',
  'alert.cooldownUnit': '초',
  'alert.forCond': '{n}s 동안',
  'alert.recentFires': '최근 발화',
  'alert.saved': '저장됨 ✓',
  'alert.save': '저장',

  // ── Metric descriptions and tips ──────────────────────────────────────────

  // Basic
  'metric.uptime.desc': 'JVM 시작 이후 경과 시간 (process.uptime).',
  'metric.uptime.tip': '값이 갑자기 0에 가깝게 재설정되면 앱이 재시작/크래시된 것입니다 — DOWN 이벤트와 연관지어 확인하세요.',
  'metric.started.desc': '프로세스가 시작된 wall-clock 시각 (process.start.time).',
  'metric.ready-time.desc': 'JVM 시작부터 앱이 요청을 받을 준비가 될 때까지 걸린 시간 (application.ready.time).',
  'metric.ready-time.tip': '배포마다 늘어나지 않는지 확인하세요 — 시작 시간 증가는 초기화 부하 또는 느린 의존성을 시사합니다.',
  'metric.cpu-process.desc': 'JVM 프로세스의 가용 코어 대비 CPU 사용률 (process.cpu.usage).',
  'metric.cpu-process.tip': '지속적으로 80% 초과 시 이 서비스는 CPU 병목입니다 — 스케일 아웃하거나 핫 패스를 프로파일링하세요. 컨테이너에서는 CPU 제한을 기준으로 합니다.',
  'metric.cpu-system.desc': '이 프로세스만이 아닌 전체 호스트/시스템 CPU 사용률 (system.cpu.usage).',
  'metric.cpu-system.tip': '시스템 CPU는 높은데 프로세스 CPU가 낮으면 호스트/노드에 노이즈 이웃이 있는 것입니다.',
  'metric.load-1m.desc': '1분 OS 부하 평균 (system.load.average.1m).',
  'metric.load-1m.tip': 'CPU 코어 수와 비교하세요: 지속적으로 부하 ≳ 코어 수이면 머신이 포화/큐잉 상태입니다.',
  'metric.cpu-count.desc': 'JVM이 인식하는 코어 수 (system.cpu.count). 컨테이너에서는 호스트가 아닌 CPU 제한값입니다.',
  'metric.cpu-count.tip': '전체 호스트 코어 수가 보인다면 (예: 14) `--cpus`/limit을 잊은 것입니다 — JVM이 이 값으로 스레드 풀 크기를 결정합니다.',
  'metric.files-open.desc': 'OS 제한 대비 열린 파일 디스크립터 수 (process.files.open / .max).',
  'metric.files-open.tip': '최대값에 근접하면 디스크립터 누수 (소켓/파일 미닫힘) → "Too many open files" 크래시 발생.',
  'metric.disk-free.desc': '앱이 실행 중인 파일시스템의 여유 공간 (disk.free / disk.total).',
  'metric.disk-free.tip': 'Docker Desktop에서는 호스트가 아닌 VM 디스크입니다. 0 이전에 미리 알림을 설정하세요 — 디스크가 가득 차면 로깅과 쓰기가 중단됩니다.',

  // Throughput & errors
  'metric.rps.desc': '초당 HTTP 요청 수 (http.server.requests count, per-second rate).',
  'metric.rps.tip': '서비스 트래픽 (Google SRE 황금 신호). UP 상태에서 0으로 급락하면 업스트림/라우팅 문제, 급증하면 부하입니다.',
  'metric.error-rate.desc': '최근 요청 중 실패한 비율 (4xx+5xx / total).',
  'metric.error-rate.tip': '오류 황금 신호. 증가율이 높아지면 보통 장애의 첫 번째 신호입니다 — 알림을 설정하세요.',
  'metric.alloc-rate.desc': '힙 할당 속도 (jvm.gc.memory.allocated, bytes/sec).',
  'metric.alloc-rate.tip': '높은 할당 변동은 GC 빈도를 높입니다. GC 증가와 함께 꾸준히 오르면 할당 부하를 프로파일링하세요.',

  // Heap
  'metric.heap-used.desc': '사용 중인 힙 vs 최대 힙 (-Xmx). 컨테이너에서 -Xmx는 메모리 제한에서 파생됩니다.',
  'metric.heap-used.tip': '순간적인 힙 톱니파형 (채워지고 GC 후 감소) — 정상입니다. GC 이후 최솟값을 관찰하세요, 피크가 아닙니다.',
  'metric.heap-eden.desc': 'G1 Eden 공간 — 새 객체가 할당되는 공간.',
  'metric.heap-eden.tip': 'minor GC마다 톱니파형이 나타납니다; 정상적입니다. 사이클 속도 ≈ minor GC 빈도.',
  'metric.heap-old.desc': 'G1 Old Gen — young gen에서 승격된 장수 객체.',
  'metric.heap-old.tip': 'GC가 회수하지 못하는 꾸준히 상승하는 하한선은 메모리 누수의 전형적인 신호입니다.',
  'metric.heap-survivor.desc': 'G1 Survivor 공간 — minor GC에서 살아남아 old gen으로 이동 중인 객체.',
  'metric.heap-committed.desc': 'OS에서 예약(committed)된 힙 메모리 — 항상 used 이상 (jvm.memory.committed, area=heap).',
  'metric.heap-committed.tip': 'committed와 used 간의 큰 차이는 정상입니다; JVM이 현재 필요보다 더 많이 예약한 것입니다. GC 이후에도 committed가 계속 증가하면 주의하세요.',

  // Non-heap
  'metric.nonheap-used.desc': '비힙 메모리: Metaspace, 코드 캐시, 스레드 스택, direct 버퍼 (-Xmx 제한 외).',
  'metric.nonheap-used.tip': '필요에 따라 증가합니다 — 채움 비율이 아닌 추세를 봐야 합니다. 컨테이너 RSS에 포함되므로 힙이 정상이더라도 OOM-kill이 발생할 수 있습니다.',
  'metric.nonheap-metaspace.desc': 'Metaspace — 로드된 클래스의 메타데이터.',
  'metric.nonheap-metaspace.tip': '정체되어야 합니다. 무한 증가는 클래스로더 누수의 신호입니다 (핫-리로드/한 JVM에서 반복 재배포 시 흔합니다).',
  'metric.nonheap-compressed-class.desc': 'Compressed class space — 클래스 포인터용 Metaspace 하위 영역.',
  'metric.nonheap-code-cache.desc': 'JIT 컴파일된 네이티브 코드 (CodeHeap).',
  'metric.nonheap-code-cache.tip': '가득 차면 JIT 컴파일이 중단되고 성능이 조용히 저하됩니다 — 한계에서 정체되는지 확인하세요.',
  'metric.nonheap-committed.desc': 'OS에서 예약된 비힙 메모리 — Metaspace, 코드 캐시 등 (jvm.memory.committed, area=nonheap).',
  'metric.nonheap-committed.tip': '새 클래스가 로드되고 JIT가 워밍업되면서 증가합니다. 정체되는 것이 정상이며, 지속적인 증가는 클래스로더 변동을 시사합니다.',
  'metric.buffers.desc': 'Direct/mapped NIO 바이트 버퍼 (jvm.buffer.memory.used) — off-heap.',
  'metric.buffers.tip': 'Netty/NIO 중심 앱에서 증가합니다; 여기서 누수가 있으면 힙은 평탄하지만 RSS가 상승합니다.',
  'metric.buffer-count.desc': '현재 할당된 direct NIO 바이트 버퍼 수 (jvm.buffer.count, id=direct).',
  'metric.buffer-count.tip': '용량은 평탄한데 수가 계속 증가하면 해제되지 않은 작은 버퍼가 많은 것입니다. RSS 증가와 연관지어 확인하세요.',

  // GC
  'metric.gc-pause-count.desc': '인터벌당 총 GC 일시 정지 횟수 (jvm.gc.pause count).',
  'metric.gc-pause-count.tip': '잦은 일시 정지 = 할당 부하. 실제 영향을 파악하려면 일시 정지 시간과 함께 확인하세요.',
  'metric.gc-pause-max.desc': '가장 긴 GC 일시 정지 시간.',
  'metric.gc-pause-max.tip': '긴 일시 정지 = 지연 스파이크 / stop-the-world 중단. 지속적으로 높으면 p99에 영향을 줍니다.',
  'metric.gc-full.desc': 'Full (major) GC 횟수 — 비용이 큰 stop-the-world 수집.',
  'metric.gc-full.tip': '정상 상태에서는 드물거나 0이어야 합니다. 잦은 Full GC = 심각한 메모리 부하 또는 누수 — 즉시 조사하세요.',
  'metric.gc-minor.desc': 'Minor (young-gen) GC 횟수 — 저렴하고 빈번한 수집.',
  'metric.gc-minor.tip': '많이 발생하는 것은 정상입니다; 속도가 극도로 높은 경우 (할당 폭풍)에만 문제입니다.',
  'metric.gc-allocated.desc': 'GC 사이에 young gen에 할당된 바이트 (jvm.gc.memory.allocated).',
  'metric.gc-promoted.desc': 'GC당 young gen에서 old gen으로 승격된 바이트.',
  'metric.gc-promoted.tip': '높은 지속적 승격은 Old Gen 증가를 유발 → 결국 Full GC. heap-old와 함께 확인하세요.',
  'metric.gc-live-data.desc': '마지막 major 수집 이후 장수 힙 크기 (jvm.gc.live.data.size).',
  'metric.gc-live-data.tip': '앱의 실제 "작업 집합". 기준선이 상승하면 누수입니다.',
  'metric.gc-max-data.desc': '수집 이후 확인된 최대 old-gen 크기.',
  'metric.gc-overhead.desc': 'GC에 소비된 wall-clock 시간의 비율 (jvm.gc.overhead).',
  'metric.gc-overhead.tip': '"GC가 나에게 해를 끼치고 있는가"에 대한 최고의 단일 지표. 25% 이상 지속 = 앱이 GC 스래싱 중 — OOM 전에 메모리를 수정하세요.',
  'metric.heap-after-gc.desc': 'GC 직후 여전히 사용 중인 old-gen 비율 (jvm.memory.usage.after.gc).',
  'metric.heap-after-gc.tip': '가장 확실한 누수/부하 신호: 높은 상태 (>90%)가 유지되면 GC가 회수 불가 → OutOfMemory 임박.',

  // Threads
  'metric.threads-live.desc': '현재 살아있는 JVM 스레드 수 (jvm.threads.live).',
  'metric.threads-live.tip': '꾸준한 증가 = 스레드 누수 (무한 실행기 / 요청당 스레드 생성).',
  'metric.threads-daemon.desc': '데몬 스레드 (배경: GC, 스케줄러, 풀).',
  'metric.threads-peak.desc': '시작 이후 최고 스레드 수.',
  'metric.threads-started.desc': '지금까지 시작된 총 스레드 수 (누적).',
  'metric.threads-started.tip': '빠르게 증가하는 합계 = 풀링 대신 요청/태스크마다 스레드 생성.',
  'metric.threads-runnable.desc': 'RUNNABLE 상태 스레드 (실행 중 또는 준비 완료).',
  'metric.threads-blocked.desc': '모니터 락에서 BLOCKED된 스레드.',
  'metric.threads-blocked.tip': '지속적인 BLOCKED 스레드 = 락 경합 — 프로파일링이 필요한 확장성 병목.',
  'metric.threads-waiting.desc': 'WAITING 상태 스레드 (파킹됨, 예: 유휴 풀 스레드).',
  'metric.threads-timed-waiting.desc': 'TIMED_WAITING 상태 스레드 (타임아웃 있는 sleep/poll).',

  // Classes & HTTP
  'metric.classes-loaded.desc': 'JVM에 현재 로드된 클래스 수 (jvm.classes.loaded).',
  'metric.classes-unloaded.desc': '시작 이후 언로드된 클래스 수.',
  'metric.classes-unloaded.tip': 'Metaspace 변동과 함께 언로드 증가는 클래스로더 재활용을 시사합니다.',
  'metric.compilation-time.desc': '총 JIT 컴파일 시간.',
  'metric.http-requests.desc': '처리된 총 HTTP 요청 수 (http.server.requests, 폴링 델타).',
  'metric.http-requests.tip': '세부 정보를 열면 엔드포인트별 분석을 볼 수 있습니다 — 어떤 라우트가 트래픽을 받는지 확인하세요.',
  'metric.http-latency-avg.desc': '평균 서버 측 요청 지연 시간 (TOTAL_TIME / COUNT).',
  'metric.http-latency-avg.tip': '평균은 테일 지연을 숨깁니다 — Max도 확인하세요. 평균 상승 = 느린 의존성 또는 GC 일시 정지.',
  'metric.http-latency-max.desc': '윈도우 내 최대 요청 지연 시간.',
  'metric.http-latency-max.tip': '최악의 경우 / p100. 스파이크를 GC 일시 정지 및 Full GC와 연관지어 확인하세요.',
  'metric.http-active.desc': '현재 처리 중인 요청 수 (http.server.requests.active).',
  'metric.http-active.tip': '증가하는데 소진되지 않는 수 = 완료보다 빠르게 쌓이는 요청 → 포화.',
  'metric.http-success.desc': '성공 (2xx) 요청 수 (outcome=SUCCESS).',
  'metric.http-client-error.desc': '4xx 클라이언트 오류 응답.',
  'metric.http-client-error.tip': '세부 정보를 열면 어떤 라우트에서 4xx가 발생하는지 엔드포인트 표에서 확인하세요. 하나의 라우트에서 급증하면 잘못된 클라이언트/계약입니다.',
  'metric.http-server-error.desc': '5xx 서버 오류 응답.',
  'metric.http-server-error.tip': '버그/장애입니다. 지속적인 5xx는 반드시 확인해야 합니다; 오류율로 알림을 설정하세요.',

  // HTTP client
  'metric.http-client-rps.desc': '아웃바운드 HTTP 클라이언트 요청 속도 (http.client.requests count/s).',
  'metric.http-client-rps.tip': '이 서비스가 하위 API를 얼마나 많이 호출하는지 보여줍니다. 인바운드 트래픽이 정상인데 감소하면 circuit breaker 오픈 또는 클라이언트 타임아웃을 의심하세요.',
  'metric.http-client-latency-avg.desc': '아웃바운드 HTTP 클라이언트 호출의 평균 지연 시간 (http.client.requests MEAN).',
  'metric.http-client-latency-avg.tip': '평균 상승 = 하위 의존성이 느려진 것입니다. max와 함께 테일 영향을 파악하세요.',
  'metric.http-client-latency-max.desc': '윈도우 내 아웃바운드 HTTP 클라이언트 호출의 최대 지연 시간 (http.client.requests MAX).',
  'metric.http-client-latency-max.tip': '서버 측 지연은 평탄한데 여기서 스파이크 = 이 서비스가 아닌 느린 업스트림 의존성이 원인입니다.',
  'metric.http-client-server-error.desc': '하위 의존성으로부터 받은 5xx 응답 (http.client.requests outcome=SERVER_ERROR).',
  'metric.http-client-server-error.tip': '의존성 상태 신호입니다. 하위 서비스에서 지속적인 5xx = 해당 서비스에 문제가 있는 것; 중요 경로를 담당하면 연쇄 장애가 발생할 수 있습니다.',

  // Logback
  'metric.log-error.desc': '인터벌당 ERROR 레벨 로그 이벤트 수 (logback.events).',
  'metric.log-error.tip': 'ERROR 로그 폭발은 보통 장애를 앞서거나 동반합니다.',
  'metric.log-warn.desc': '인터벌당 WARN 레벨 로그 이벤트 수.',
  'metric.log-info.desc': '인터벌당 INFO 레벨 로그 이벤트 수.',

  // HikariCP
  'metric.hikari-active.desc': 'Hikari 풀에서 활성 (사용 중인) DB 연결 수.',
  'metric.hikari-idle.desc': '풀에서 사용 가능한 유휴 DB 연결 수.',
  'metric.hikari-pending.desc': 'DB 연결을 빌리기 위해 대기 중인 스레드 수.',
  'metric.hikari-pending.tip': '0 이상이 지속되면 풀 소진 — 요청이 연결을 기다리며 차단됩니다. 풀 크기를 늘리거나 느린 쿼리를 수정하세요.',
  'metric.hikari-total.desc': 'HikariCP 풀의 전체 연결 수 (hikaricp.connections) — 활성 + 유휴.',
  'metric.hikari-max.desc': 'HikariCP의 구성된 최대 풀 크기 (hikaricp.connections.max).',

  // JDBC
  'metric.jdbc-active.desc': '현재 사용 중인 JDBC 연결 수 (jdbc.connections.active) — 범용 Spring 풀 메트릭.',
  'metric.jdbc-active.tip': 'jdbc-max에 근접 = 풀이 곧 소진됩니다. 연결을 기다리며 요청이 차단됩니다 — 연결을 오래 잡고 있는 느린 쿼리를 확인하세요.',
  'metric.jdbc-idle.desc': '체크아웃 가능한 유휴 JDBC 풀 연결 수 (jdbc.connections.idle).',
  'metric.jdbc-max.desc': '범용 Spring JDBC 풀의 최대 풀 크기 구성값 (jdbc.connections.max).',
  'metric.jdbc-min.desc': 'JDBC 풀의 최소 유휴 연결 구성값 (jdbc.connections.min).',

  // Hibernate
  'metric.hibernate-sessions-open.desc': '초당 열린 Hibernate 세션 수 (hibernate.sessions.open count/s).',
  'metric.hibernate-sessions-open.tip': '높은 속도 = 짧은 세션이 많음. 각 세션은 트랜잭션을 수반하며, 부하가 일정한데 속도가 오르면 요청당 세션 패턴을 검토하세요.',
  'metric.hibernate-transactions.desc': 'Hibernate가 실행한 DB 트랜잭션 수 (hibernate.transactions).',
  'metric.hibernate-transactions.tip': '트랜잭션 수가 요청 수를 훨씬 초과하면 요청당 여러 트랜잭션이 실행되는 것 — @Transactional 경계를 검토하세요.',
  'metric.hibernate-connections-obtained.desc': 'Hibernate가 풀에서 획득한 JDBC 연결 수 (hibernate.connections.obtained).',
  'metric.hibernate-connections-obtained.tip': '트랜잭션 수를 밀접하게 따릅니다. 트랜잭션보다 훨씬 높으면 연결 누수 또는 과도한 연결 획득을 확인하세요.',
  'metric.hibernate-statements.desc': 'Hibernate가 준비한 JDBC statement 수 (hibernate.statements, status=prepared).',
  'metric.hibernate-statements.tip': '요청당 높은 수는 N+1 쿼리 냄새입니다 — Hibernate SQL 로깅 또는 쿼리 검사기로 원인을 찾으세요.',
  'metric.hibernate-query-executions.desc': 'Hibernate가 실행한 총 쿼리 수 (hibernate.query.executions).',
  'metric.hibernate-query-executions.tip': '쿼리 볼륨을 추적합니다. 트랜잭션 대비 쿼리 비율이 예상보다 높은지 비교하세요.',
  'metric.hibernate-query-max.desc': '관찰된 가장 느린 쿼리 실행 시간 (hibernate.query.executions MAX).',
  'metric.hibernate-query-max.tip': '단일 최악의 쿼리. 평균은 정상인데 max가 크면 간헐적 이상 쿼리입니다 — slow query 로그에서 찾으세요.',
  'metric.hibernate-2lc-hit.desc': '2차 레벨 캐시 적중 수 (hibernate.second.level.cache.requests, result=hit).',
  'metric.hibernate-2lc-hit.tip': '높을수록 좋습니다. 적중이 낮고 미스가 높으면 L2 캐시가 너무 작거나 엔티티 조회 패턴이 캐시를 무력화합니다.',
  'metric.hibernate-2lc-miss.desc': '2차 레벨 캐시 미스 수 (hibernate.second.level.cache.requests, result=miss).',
  'metric.hibernate-2lc-miss.tip': '높은 미스 비율 (miss / (hit + miss)) = L2 캐시가 효과적이지 않음 — 캐시 region 크기와 eviction 정책을 검토하세요.',
  'metric.hibernate-flushes.desc': 'Hibernate 세션 flush — persistence context가 DB에 동기화될 때 (hibernate.flushes).',

  // Spring Cache
  'metric.cache-gets-hit.desc': 'Spring 캐시 적중 — 캐시에서 제공된 요청 수 (cache.gets, result=hit).',
  'metric.cache-gets-hit.tip': '높은 적중률이 목표입니다. 적중 vs 미스를 비교하여 캐시 효과를 측정하세요.',
  'metric.cache-gets-miss.desc': 'Spring 캐시 미스 — 원본으로 통과된 조회 수 (cache.gets, result=miss).',
  'metric.cache-gets-miss.tip': '증가하거나 지배적인 미스율은 캐시 효과를 감소시키고 backing store에 부하를 가중합니다. TTL, eviction 정책, key 카디널리티를 확인하세요.',
  'metric.cache-puts.desc': 'Spring 캐시에 쓰인 항목 수 (cache.puts).',
  'metric.cache-evictions.desc': 'Spring 캐시에서 eviction된 항목 수 (cache.evictions).',
  'metric.cache-evictions.tip': '심한 eviction 압력 = 캐시가 작업 집합에 비해 너무 작습니다. 최대 크기를 늘리거나 TTL 설정을 검토하세요.',
  'metric.cache-size.desc': 'Spring 캐시에 현재 보유된 예상 항목 수 (cache.size).',

  // Tomcat
  'metric.tomcat-sessions-active.desc': '활성 HTTP 세션 수.',
  'metric.tomcat-sessions-created.desc': '생성된 총 세션 수.',
  'metric.tomcat-sessions-expired.desc': '만료된 (타임아웃) 세션 수.',
  'metric.tomcat-sessions-rejected.desc': '최대치 도달로 거부된 세션 수.',
  'metric.tomcat-sessions-rejected.tip': '0 이상 = 세션 한도 도달 — 사용자가 거부되고 있습니다.',
  'metric.tomcat-threads-busy.desc': '바쁜 Tomcat 워커 스레드 수.',
  'metric.tomcat-threads-busy.tip': '최대 커넥터 스레드에 근접 = 웹 티어가 포화됨; 새 요청이 큐에 쌓입니다.',
  'metric.tomcat-threads-current.desc': '현재 Tomcat 워커 스레드 수 (tomcat.threads.current).',
  'metric.tomcat-threads-current.tip': 'tomcat-threads-max와 비교하세요: 최대에 근접하면 스레드 풀이 완전히 활용되고 있고 새 요청은 큐에 쌓입니다.',
  'metric.tomcat-threads-max.desc': '구성된 최대 Tomcat 워커 스레드 수 (tomcat.threads.config.max).',
  'metric.tomcat-connections-current.desc': 'Tomcat이 현재 보유 중인 열린 연결 수 (tomcat.connections.current).',
  'metric.tomcat-connections-current.tip': 'tomcat-connections-max에 근접 = 웹 티어가 포화됩니다. 클라이언트가 연결 거부 오류를 받기 시작합니다.',
  'metric.tomcat-connections-max.desc': 'Tomcat이 허용하는 최대 동시 연결 수 (tomcat.connections.max).',
  'metric.tomcat-global-requests.desc': 'Tomcat 커넥터의 총 요청 처리량 (tomcat.global.request count/s).',
  'metric.tomcat-global-requests.tip': '커넥터 수준의 원시 요청 속도입니다. http.server.requests와 비교하여 내부 라우팅 불일치를 감지하세요.',
  'metric.tomcat-global-request-max.desc': 'Tomcat 커넥터에서 관찰된 가장 느린 요청 (tomcat.global.request MAX).',
  'metric.tomcat-global-request-max.tip': '커넥터 수준의 최악 케이스 지연 시간. 스파이크를 GC 일시 정지 이벤트 또는 하위 타임아웃과 연관지어 확인하세요.',
  'metric.tomcat-global-errors.desc': 'Tomcat 커넥터 수준에서 오류로 집계된 요청 수 (tomcat.global.error).',
  'metric.tomcat-global-errors.tip': '커넥터 수준 오류는 애플리케이션 코드에 도달하지 못한 네트워크 중단 및 조기 연결 해제를 포함합니다. 지속적인 수는 반드시 조사하세요.',
  'metric.tomcat-bytes-sent.desc': 'Tomcat 커넥터의 초당 전송 바이트 수 (tomcat.global.sent bytes/s).',
  'metric.tomcat-bytes-sent.tip': '아웃바운드 바이트 급증 = 큰 응답 페이로드 또는 고대역폭 엔드포인트. egress 포화 진단에 유용합니다.',
  'metric.tomcat-bytes-received.desc': 'Tomcat 커넥터의 초당 수신 바이트 수 (tomcat.global.received bytes/s).',
  'metric.tomcat-bytes-received.tip': 'RPS는 정상인데 인바운드 트래픽이 높으면 큰 요청 본문 (파일 업로드, 대량 페이로드)입니다.',
  'metric.tomcat-sessions-active-max.desc': '관찰된 최고 동시 활성 HTTP 세션 수 (tomcat.sessions.active.max).',

  // Scheduled tasks
  'metric.sched-exec.desc': '인터벌당 @Scheduled 태스크 실행 수 (tasks.scheduled.execution).',
  'metric.sched-active.desc': '현재 실행 중인 스케줄드 태스크 수.',
  'metric.sched-active.tip': '오래 0 이상이면 태스크가 중단되거나 자체 스케줄과 겹칠 수 있습니다.',

  // Task executor (@Async / ThreadPoolTaskExecutor)
  'metric.executor-active.desc': 'Spring 태스크 실행기 풀에서 현재 태스크를 실행 중인 스레드 수 (executor.active).',
  'metric.executor-active.tip': '지속적으로 pool size에 근접하면 실행기가 항상 바쁜 것입니다. executor-queued와 함께 작업이 밀리는지 확인하세요.',
  'metric.executor-queued.desc': '여유 스레드를 기다리며 실행기 큐에 대기 중인 태스크 수 (executor.queued).',
  'metric.executor-queued.tip': '큐 깊이가 지속적으로 있으면 풀이 제출된 작업을 따라가지 못하는 것입니다. 풀 크기를 늘리거나 태스크 실행 시간을 줄이세요.',
  'metric.executor-pool-size.desc': 'Spring 태스크 실행기 풀의 현재 스레드 수 (executor.pool.size).',
  'metric.executor-completed.desc': '실행기가 완료한 총 태스크 수 (executor.completed).',
  'metric.executor-completed.tip': '누적 카운터입니다. 이 메트릭에서 도출한 속도는 실행기 처리량을 나타냅니다 — active와 비교하여 활용도를 평가하세요.',
}

const dicts: Record<Lang, Dict> = { en, ko }

// ─── Translator ───────────────────────────────────────────────────────────────

/** Non-hook translator: look up key in lang, fall back to en, then to the key itself. */
export function translate(lang: Lang, key: string, params?: Record<string, string | number>): string {
  let s = dicts[lang][key] ?? dicts.en[key] ?? key
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      s = s.replaceAll(`{${k}}`, String(v))
    }
  }
  return s
}

/** React hook: returns a translator bound to the current language. Re-renders on lang change. */
export function useT(): (key: string, params?: Record<string, string | number>) => string {
  const lang = useLang()
  return (key, params) => translate(lang, key, params)
}
