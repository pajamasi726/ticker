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

  // JVM memory — non-heap
  'metric.nonheap-used.desc': 'Non-heap memory: Metaspace, code cache, thread stacks, direct buffers (not bounded by -Xmx).',
  'metric.nonheap-used.tip': 'Grows on demand — a trend, not a fill ratio. Still counts toward container RSS, so it can cause an OOM-kill even when heap is fine.',
  'metric.nonheap-metaspace.desc': 'Metaspace — class metadata for loaded classes.',
  'metric.nonheap-metaspace.tip': 'Should plateau. Unbounded growth = a classloader leak (common with hot-reload / repeated redeploys in one JVM).',
  'metric.nonheap-compressed-class.desc': 'Compressed class space — a sub-region of Metaspace for class pointers.',
  'metric.nonheap-code-cache.desc': 'JIT-compiled native code (CodeHeap).',
  'metric.nonheap-code-cache.tip': 'If it fills, the JIT stops compiling and performance quietly degrades — watch for a plateau at the ceiling.',
  'metric.buffers.desc': 'Direct/mapped NIO byte buffers (jvm.buffer.memory.used) — off-heap.',
  'metric.buffers.tip': 'Netty/NIO-heavy apps grow this; a leak here shows as rising RSS with flat heap.',

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

  // Tomcat
  'metric.tomcat-sessions-active.desc': 'Active HTTP sessions.',
  'metric.tomcat-sessions-created.desc': 'Total sessions created.',
  'metric.tomcat-sessions-expired.desc': 'Sessions expired (timed out).',
  'metric.tomcat-sessions-rejected.desc': 'Sessions rejected because the max was reached.',
  'metric.tomcat-sessions-rejected.tip': 'Anything >0 = you hit the session cap — users are being turned away.',
  'metric.tomcat-threads-busy.desc': 'Busy Tomcat worker threads.',
  'metric.tomcat-threads-busy.tip': 'Near the max connector threads = the web tier is saturated; new requests queue.',

  // Scheduled tasks
  'metric.sched-exec.desc': 'Scheduled @Scheduled task executions per interval (tasks.scheduled.execution).',
  'metric.sched-active.desc': 'Scheduled tasks currently running.',
  'metric.sched-active.tip': 'A task stuck >0 for long may be hung or overlapping its own schedule.',
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

  // Non-heap
  'metric.nonheap-used.desc': '비힙 메모리: Metaspace, 코드 캐시, 스레드 스택, direct 버퍼 (-Xmx 제한 외).',
  'metric.nonheap-used.tip': '필요에 따라 증가합니다 — 채움 비율이 아닌 추세를 봐야 합니다. 컨테이너 RSS에 포함되므로 힙이 정상이더라도 OOM-kill이 발생할 수 있습니다.',
  'metric.nonheap-metaspace.desc': 'Metaspace — 로드된 클래스의 메타데이터.',
  'metric.nonheap-metaspace.tip': '정체되어야 합니다. 무한 증가는 클래스로더 누수의 신호입니다 (핫-리로드/한 JVM에서 반복 재배포 시 흔합니다).',
  'metric.nonheap-compressed-class.desc': 'Compressed class space — 클래스 포인터용 Metaspace 하위 영역.',
  'metric.nonheap-code-cache.desc': 'JIT 컴파일된 네이티브 코드 (CodeHeap).',
  'metric.nonheap-code-cache.tip': '가득 차면 JIT 컴파일이 중단되고 성능이 조용히 저하됩니다 — 한계에서 정체되는지 확인하세요.',
  'metric.buffers.desc': 'Direct/mapped NIO 바이트 버퍼 (jvm.buffer.memory.used) — off-heap.',
  'metric.buffers.tip': 'Netty/NIO 중심 앱에서 증가합니다; 여기서 누수가 있으면 힙은 평탄하지만 RSS가 상승합니다.',

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

  // Tomcat
  'metric.tomcat-sessions-active.desc': '활성 HTTP 세션 수.',
  'metric.tomcat-sessions-created.desc': '생성된 총 세션 수.',
  'metric.tomcat-sessions-expired.desc': '만료된 (타임아웃) 세션 수.',
  'metric.tomcat-sessions-rejected.desc': '최대치 도달로 거부된 세션 수.',
  'metric.tomcat-sessions-rejected.tip': '0 이상 = 세션 한도 도달 — 사용자가 거부되고 있습니다.',
  'metric.tomcat-threads-busy.desc': '바쁜 Tomcat 워커 스레드 수.',
  'metric.tomcat-threads-busy.tip': '최대 커넥터 스레드에 근접 = 웹 티어가 포화됨; 새 요청이 큐에 쌓입니다.',

  // Scheduled tasks
  'metric.sched-exec.desc': '인터벌당 @Scheduled 태스크 실행 수 (tasks.scheduled.execution).',
  'metric.sched-active.desc': '현재 실행 중인 스케줄드 태스크 수.',
  'metric.sched-active.tip': '오래 0 이상이면 태스크가 중단되거나 자체 스케줄과 겹칠 수 있습니다.',
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
