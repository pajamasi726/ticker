import { useCallback, useEffect, useState } from 'react'
import type { AdminInfo, AdminTarget, AlertFire, AlertRule, BackupFile, HistoryStats, SilenceView } from '../types'
import {
  ApiError, backupDownloadUrl, clearSilence, fetchAdminInfo, fetchAdminTargets, fetchAlertRules,
  fetchBackups, fetchHistoryStats, fetchRecentAlerts, fetchSilence, removeTarget, startSilence,
  triggerBackup, updateAlertRule,
} from '../api'
import { formatValue } from '../format'
import { useT } from '../i18n'

const POLL_MS = 5000

function humanBytes(n: number | null | undefined): string {
  if (n == null) return '—'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function humanMillis(ms: number | null | undefined): string {
  if (ms == null) return '—'
  const s = Math.round(ms / 1000)
  if (s < 60) return `${s}s`
  if (s < 3600) return `${Math.floor(s / 60)}m ${s % 60}s`
  if (s < 86400) return `${Math.floor(s / 3600)}h ${Math.floor((s % 3600) / 60)}m`
  return `${Math.floor(s / 86400)}d ${Math.floor((s % 86400) / 3600)}h`
}

function ago(now: number, t: number | null): string {
  if (t == null) return '—'
  return humanMillis(now - t)
}

export function AdminPanel({ onClose }: { onClose: () => void }) {
  const t = useT()
  const [stats, setStats] = useState<HistoryStats | null>(null)
  const [backups, setBackups] = useState<BackupFile[]>([])
  const [silence, setSilence] = useState<SilenceView | null>(null)
  const [rules, setRules] = useState<AlertRule[]>([])
  const [recent, setRecent] = useState<AlertFire[]>([])
  const [targets, setTargets] = useState<AdminTarget[]>([])
  const [info, setInfo] = useState<AdminInfo | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const [busy, setBusy] = useState(false)
  const [backupMsg, setBackupMsg] = useState<string | null>(null)
  const [minutes, setMinutes] = useState('10')

  const reload = useCallback(() => {
    fetchHistoryStats().then(setStats).catch(() => {})
    fetchBackups().then(setBackups).catch(() => {})
    fetchSilence().then(setSilence).catch(() => {})
    fetchAlertRules().then(setRules).catch(() => {})
    fetchRecentAlerts().then((a) => setRecent(a.slice(0, 5))).catch(() => {})
    fetchAdminTargets().then(setTargets).catch(() => {})
    setNow(Date.now())
  }, [])

  useEffect(() => {
    fetchAdminInfo().then(setInfo).catch(() => {})
    reload()
    const id = setInterval(reload, POLL_MS)
    return () => clearInterval(id)
  }, [reload])

  const onBackup = () => {
    setBusy(true)
    setBackupMsg(null)
    triggerBackup()
      .then((r) => {
        setBackupMsg(t('admin.backup.done', { name: r.file.split('/').pop() ?? r.file, size: humanBytes(r.bytes), ms: r.tookMs }))
        reload()
      })
      .catch((e) => setBackupMsg(e instanceof ApiError ? e.message : String(e)))
      .finally(() => setBusy(false))
  }

  const onSilence = () => {
    const m = parseInt(minutes, 10)
    if (!Number.isFinite(m) || m <= 0) return
    startSilence(m).then(setSilence).catch(() => {})
  }

  const toggleRule = (r: AlertRule) => {
    updateAlertRule(r.key, { enabled: !r.enabled })
      .then((updated) => setRules((rs) => rs.map((x) => (x.key === updated.key ? updated : x))))
      .catch(() => {})
  }

  const range = stats?.oldestTsMillis != null && stats?.newestTsMillis != null
    ? `${humanMillis(stats.newestTsMillis - stats.oldestTsMillis)}`
    : '—'

  return (
    <div className="admin">
      <header className="detail-header">
        <button className="detail-back" onClick={onClose}>{t('admin.back')}</button>
        <h2 className="detail-title">{t('admin.title')}</h2>
        {info && <span className="detail-type">v{info.version}</span>}
        {info && <span className="detail-uptime">{t('admin.uptime', { v: humanMillis(info.uptimeMillis) })}</span>}
      </header>

      <div className="admin__grid">
        {/* ---- Storage & Backup ---- */}
        <section className="admin-card">
          <h3 className="admin-card__title">{t('admin.storage.title')}</h3>
          {!stats ? <p className="admin-muted">…</p> : !stats.enabled ? (
            <p className="admin-muted">{t('admin.storage.disabled')}</p>
          ) : (
            <>
              <dl className="admin-kv">
                <dt>{t('admin.storage.db')}</dt><dd>{stats.db}</dd>
                <dt>{t('admin.storage.rows')}</dt><dd>{stats.rowCount?.toLocaleString() ?? '—'}</dd>
                <dt>{t('admin.storage.span')}</dt><dd>{range}</dd>
                {stats.h2FileBytes != null && <><dt>{t('admin.storage.file')}</dt><dd>{humanBytes(stats.h2FileBytes)}</dd></>}
                <dt>{t('admin.storage.retention')}</dt><dd>{humanMillis(stats.retentionMillis)}</dd>
                <dt>{t('admin.storage.archive')}</dt>
                <dd>
                  {stats.archive?.enabled
                    ? t('admin.storage.archiveOn', { n: stats.archive.fileCount, size: humanBytes(stats.archive.totalBytes) })
                    : t('admin.storage.archiveOff')}
                </dd>
              </dl>

              <div className="admin-actions">
                {stats.backupSupported ? (
                  <button className="admin-btn admin-btn--primary" onClick={onBackup} disabled={busy}>
                    {busy ? t('admin.backup.running') : t('admin.backup.now')}
                  </button>
                ) : (
                  <span className="admin-muted">{t('admin.backup.unsupported')}</span>
                )}
              </div>
              {backupMsg && <p className="admin-note" role="status">{backupMsg}</p>}

              {backups.length > 0 && (
                <table className="admin-table">
                  <thead><tr><th>{t('admin.backup.file')}</th><th>{t('admin.backup.size')}</th><th>{t('admin.backup.when')}</th><th /></tr></thead>
                  <tbody>
                    {backups.map((b) => (
                      <tr key={b.name}>
                        <td className="admin-mono">{b.name}</td>
                        <td>{humanBytes(b.bytes)}</td>
                        <td>{ago(now, b.createdAtMillis)} {t('admin.ago')}</td>
                        <td><a className="admin-link" href={backupDownloadUrl(b.name)}>{t('admin.backup.download')}</a></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </>
          )}
        </section>

        {/* ---- Silence & Alerts ---- */}
        <section className="admin-card">
          <h3 className="admin-card__title">{t('admin.alerts.title')}</h3>
          <dl className="admin-kv">
            <dt>{t('admin.alerts.enabled')}</dt><dd>{info ? (info.alert.enabled ? t('admin.on') : t('admin.off')) : '—'}</dd>
            <dt>{t('admin.alerts.webhook')}</dt><dd>{info ? (info.alert.webhookConfigured ? t('admin.configured') : t('admin.notConfigured')) : '—'}</dd>
          </dl>

          <div className="admin-silence">
            <span className={`admin-chip ${silence?.active ? 'admin-chip--warn' : ''}`}>
              {silence?.active ? t('admin.silence.active', { until: silence.until ? new Date(silence.until).toLocaleTimeString() : '' }) : t('admin.silence.inactive')}
            </span>
            {silence?.active ? (
              <button className="admin-btn" onClick={() => { clearSilence().then(setSilence).catch(() => {}) }}>{t('admin.silence.clear')}</button>
            ) : (
              <>
                <input
                  className="admin-input" type="number" min="1" max="1440" value={minutes}
                  onChange={(e) => setMinutes(e.target.value)} aria-label={t('admin.silence.minutes')}
                />
                <button className="admin-btn" onClick={onSilence}>{t('admin.silence.start')}</button>
              </>
            )}
          </div>
          <p className="admin-muted admin-small">{t('admin.silence.hint')}</p>

          <table className="admin-table">
            <thead><tr><th>{t('admin.rules.rule')}</th><th>{t('admin.rules.threshold')}</th><th>{t('admin.rules.enabled')}</th></tr></thead>
            <tbody>
              {rules.map((r) => (
                <tr key={r.key}>
                  <td>{r.label}</td>
                  <td className="admin-mono">{r.comparator === 'GT' ? '>' : '<'} {formatValue(r.threshold, r.unit)}</td>
                  <td>
                    <button className={`admin-toggle ${r.enabled ? 'admin-toggle--on' : ''}`} onClick={() => toggleRule(r)} aria-pressed={r.enabled}>
                      {r.enabled ? t('admin.on') : t('admin.off')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {recent.length > 0 && (
            <>
              <h4 className="admin-card__sub">{t('admin.alerts.recent')}</h4>
              <ul className="admin-fires">
                {recent.map((f, i) => (
                  <li key={i}><span className="admin-mono">{f.targetName}</span> — {f.label} {formatValue(f.value, f.unit)}</li>
                ))}
              </ul>
            </>
          )}
        </section>

        {/* ---- Target registry ---- */}
        <section className="admin-card admin-card--wide">
          <h3 className="admin-card__title">{t('admin.registry.title', { n: targets.length })}</h3>
          <table className="admin-table">
            <thead>
              <tr>
                <th>{t('admin.registry.name')}</th><th>{t('admin.registry.instance')}</th><th>IP</th>
                <th>{t('admin.registry.source')}</th><th>{t('admin.registry.heartbeat')}</th><th />
              </tr>
            </thead>
            <tbody>
              {targets.map((tg) => (
                <tr key={tg.id}>
                  <td>{tg.name}</td>
                  <td className="admin-mono">{tg.instance ?? tg.url}</td>
                  <td className="admin-mono">{tg.ip ?? '—'}</td>
                  <td><span className={`admin-chip admin-chip--${tg.source.toLowerCase()}`}>{tg.source}</span></td>
                  <td>{tg.lastSeenMillis != null ? `${ago(now, tg.lastSeenMillis)} ${t('admin.ago')}` : '—'}</td>
                  <td>
                    {tg.source !== 'STATIC' && (
                      <button className="admin-btn admin-btn--danger" onClick={() => { removeTarget(tg.id).then(reload).catch(() => {}) }}>
                        {t('admin.registry.remove')}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        {/* ---- Collector ---- */}
        <section className="admin-card admin-card--wide">
          <h3 className="admin-card__title">{t('admin.collector.title')}</h3>
          {info && (
            <dl className="admin-kv admin-kv--cols">
              <dt>{t('admin.collector.pollInterval')}</dt><dd>{humanMillis(info.poll.intervalMillis)}</dd>
              <dt>{t('admin.collector.timeout')}</dt><dd>{humanMillis(info.poll.timeoutMillis)}</dd>
              <dt>{t('admin.collector.threshold')}</dt><dd>×{info.poll.failureThreshold}</dd>
              <dt>{t('admin.collector.degraded')}</dt><dd>{info.poll.degradedLatencyMs}ms</dd>
              <dt>{t('admin.collector.basePath')}</dt><dd className="admin-mono">{info.server.basePath ?? '—'}</dd>
              <dt>{t('admin.collector.publicUrl')}</dt><dd>{info.server.publicUrlConfigured ? t('admin.configured') : t('admin.notConfigured')}</dd>
              <dt>{t('admin.collector.expiry')}</dt><dd>{info.server.registrationExpiryMillis > 0 ? humanMillis(info.server.registrationExpiryMillis) : t('admin.off')}</dd>
              <dt>{t('admin.collector.history')}</dt><dd>{info.history.enabled ? `${t('admin.on')} (${info.history.db})` : t('admin.off')}</dd>
            </dl>
          )}
        </section>
      </div>
    </div>
  )
}
