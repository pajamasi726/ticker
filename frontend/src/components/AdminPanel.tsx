import { useCallback, useEffect, useRef, useState } from 'react'
import type { AdminInfo, AdminTarget, AlertFire, AlertRule, BackupFile, HistoryStats, SilenceView } from '../types'
import {
  ApiError, backupDownloadUrl, clearSilence, deleteBackup, fetchAdminInfo, fetchAdminTargets,
  fetchAlertRules, fetchBackups, fetchHistoryStats, fetchRecentAlerts, fetchSilence, removeTarget,
  restoreBackup, startSilence, triggerBackup, updateAlertRule, uploadBackup,
} from '../api'
import { formatValue } from '../format'
import { useT } from '../i18n'

const POLL_MS = 5000
const SILENCE_PRESETS = [10, 30, 60]

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

/** Kept as data, translated at render time — so the note follows a language switch. */
type BackupNote = { kind: 'ok'; name: string; bytes: number; ms: number } | { kind: 'err'; msg: string }

const STATE_DOT: Record<AdminTarget['state'], string> = {
  UP: 'admin-dot--up', DEGRADED: 'admin-dot--degraded', DOWN: 'admin-dot--down', UNKNOWN: 'admin-dot--unknown',
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
  const [note, setNote] = useState<BackupNote | null>(null)
  const [minutes, setMinutes] = useState('10')
  const fileRef = useRef<HTMLInputElement | null>(null)
  const [editingRule, setEditingRule] = useState<string | null>(null)
  const [editValue, setEditValue] = useState('')

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
    setNote(null)
    triggerBackup()
      .then((r) => {
        setNote({ kind: 'ok', name: r.file.split('/').pop() ?? r.file, bytes: r.bytes, ms: r.tookMs })
        reload()
      })
      .catch((e) => setNote({ kind: 'err', msg: e instanceof ApiError ? e.message : String(e) }))
      .finally(() => setBusy(false))
  }

  const onDeleteBackup = (name: string) => {
    if (!window.confirm(t('admin.backup.deleteConfirm', { name }))) return
    deleteBackup(name).then(reload).catch(() => {})
  }

  const onRestore = (name: string) => {
    if (!window.confirm(t('admin.backup.restoreConfirm', { name }))) return
    setBusy(true)
    setNote(null)
    restoreBackup(name)
      .then((r) => { setNote({ kind: 'ok', name: t('admin.backup.restored', { rows: r.rows.toLocaleString(), ms: r.tookMs }), bytes: -1, ms: -1 }); reload() })
      .catch((e) => setNote({ kind: 'err', msg: e instanceof ApiError ? e.message : String(e) }))
      .finally(() => setBusy(false))
  }

  const onUpload = (file: File | undefined) => {
    if (!file) return
    setBusy(true)
    setNote(null)
    uploadBackup(file)
      .then((r) => { setNote({ kind: 'ok', name: t('admin.backup.uploaded', { name: r.name }), bytes: -1, ms: -1 }); reload() })
      .catch((e) => setNote({ kind: 'err', msg: e instanceof ApiError ? e.message : String(e) }))
      .finally(() => { setBusy(false); if (fileRef.current) fileRef.current.value = '' })
  }

  const onSilence = (m: number) => {
    if (!Number.isFinite(m) || m <= 0) return
    startSilence(m).then(setSilence).catch(() => {})
  }

  const toggleRule = (r: AlertRule) => {
    updateAlertRule(r.key, { enabled: !r.enabled })
      .then((u) => setRules((rs) => rs.map((x) => (x.key === u.key ? u : x))))
      .catch(() => {})
  }

  // PERCENT rules store ratios (0.75 = 75%) — edit in the unit people read on screen.
  const beginEdit = (r: AlertRule) => {
    setEditingRule(r.key)
    setEditValue(r.unit === 'PERCENT' ? String(Math.round(r.threshold * 1000) / 10) : String(r.threshold))
  }
  const commitEdit = (r: AlertRule) => {
    const raw = parseFloat(editValue)
    setEditingRule(null)
    if (!Number.isFinite(raw)) return
    const threshold = r.unit === 'PERCENT' ? raw / 100 : raw
    updateAlertRule(r.key, { threshold })
      .then((u) => setRules((rs) => rs.map((x) => (x.key === u.key ? u : x))))
      .catch(() => {})
  }

  const onRemoveTarget = (tg: AdminTarget) => {
    if (!window.confirm(t('admin.registry.removeConfirm', { name: tg.instance ?? tg.name }))) return
    removeTarget(tg.id).then(reload).catch(() => {})
  }

  const span = stats?.oldestTsMillis != null && stats?.newestTsMillis != null
    ? humanMillis(stats.newestTsMillis - stats.oldestTsMillis)
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
          <h3 className="admin-card__title"><span aria-hidden>💾</span> {t('admin.storage.title')}</h3>
          {!stats ? <p className="admin-muted">…</p> : !stats.enabled ? (
            <p className="admin-muted">{t('admin.storage.disabled')}</p>
          ) : (
            <>
              <dl className="admin-kv">
                <dt>{t('admin.storage.db')}</dt><dd>{stats.db}</dd>
                <dt>{t('admin.storage.rows')}</dt><dd>{stats.rowCount?.toLocaleString() ?? '—'}</dd>
                <dt>{t('admin.storage.span')}</dt><dd>{span}</dd>
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
                  <>
                    <button className="admin-btn admin-btn--primary" onClick={onBackup} disabled={busy}>
                      {busy ? t('admin.backup.running') : `⬇ ${t('admin.backup.now')}`}
                    </button>
                    <button className="admin-btn" onClick={() => fileRef.current?.click()} disabled={busy}>
                      ⬆ {t('admin.backup.upload')}
                    </button>
                    <input
                      ref={fileRef} type="file" accept=".zip" style={{ display: 'none' }}
                      onChange={(e) => onUpload(e.target.files?.[0])}
                    />
                  </>
                ) : (
                  <span className="admin-muted">{t('admin.backup.unsupported')}</span>
                )}
              </div>
              {note && (
                <p className={`admin-note ${note.kind === 'err' ? 'admin-note--err' : ''}`} role="status">
                  {note.kind === 'ok'
                    ? (note.bytes < 0 ? note.name : t('admin.backup.done', { name: note.name, size: humanBytes(note.bytes), ms: note.ms }))
                    : note.msg}
                </p>
              )}

              {stats.backupSupported && (backups.length === 0 ? (
                <p className="admin-muted admin-small">{t('admin.backup.empty')}</p>
              ) : (
                <table className="admin-table admin-table--backups">
                  <thead><tr><th>{t('admin.backup.file')}</th><th>{t('admin.backup.size')}</th><th>{t('admin.backup.when')}</th><th className="admin-table__actions" /></tr></thead>
                  <tbody>
                    {backups.map((b) => (
                      <tr key={b.name}>
                        <td className="admin-mono admin-ellipsis" title={b.name}>{b.name}</td>
                        <td className="admin-nowrap">{humanBytes(b.bytes)}</td>
                        <td className="admin-nowrap" title={new Date(b.createdAtMillis).toLocaleString()}>
                          {humanMillis(now - b.createdAtMillis)} {t('admin.ago')}
                        </td>
                        <td className="admin-table__actions admin-nowrap">
                          <a className="admin-iconbtn" href={backupDownloadUrl(b.name)} title={t('admin.backup.download')} aria-label={t('admin.backup.download')}>⬇</a>
                          <button className="admin-iconbtn" onClick={() => onRestore(b.name)} disabled={busy} title={t('admin.backup.restore')} aria-label={t('admin.backup.restore')}>↩</button>
                          <button className="admin-iconbtn admin-iconbtn--danger" onClick={() => onDeleteBackup(b.name)} disabled={busy} title={t('admin.backup.delete')} aria-label={t('admin.backup.delete')}>🗑</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ))}
            </>
          )}
        </section>

        {/* ---- Silence & Alerts ---- */}
        <section className="admin-card">
          <h3 className="admin-card__title"><span aria-hidden>🔕</span> {t('admin.alerts.title')}</h3>
          <dl className="admin-kv">
            <dt>{t('admin.alerts.enabled')}</dt><dd>{info ? (info.alert.enabled ? t('admin.on') : t('admin.off')) : '—'}</dd>
            <dt>{t('admin.alerts.webhook')}</dt><dd>{info ? (info.alert.webhookConfigured ? t('admin.configured') : t('admin.notConfigured')) : '—'}</dd>
          </dl>

          <div className="admin-silence">
            {silence?.active ? (
              <div className="admin-silence__row">
                <span className="admin-chip admin-chip--warn">
                  {t('admin.silence.active', { until: silence.until ? new Date(silence.until).toLocaleTimeString() : '' })}
                </span>
                <button className="admin-btn admin-btn--warn" onClick={() => { clearSilence().then(setSilence).catch(() => {}) }}>
                  {t('admin.silence.clear')}
                </button>
              </div>
            ) : (
              <>
                <div className="admin-silence__row">
                  <span className="admin-muted admin-small">{t('admin.silence.label')}</span>
                  {SILENCE_PRESETS.map((m) => (
                    <button key={m} className="admin-btn" onClick={() => onSilence(m)}>
                      {t('admin.silence.preset', { m })}
                    </button>
                  ))}
                </div>
                <div className="admin-silence__row">
                  <input
                    className="admin-input" type="number" min="1" max="1440" value={minutes}
                    onChange={(e) => setMinutes(e.target.value)} aria-label={t('admin.silence.minutes')}
                  />
                  <span className="admin-muted admin-small">{t('admin.silence.minutes')}</span>
                  <button className="admin-btn" onClick={() => onSilence(parseInt(minutes, 10))}>{t('admin.silence.start')}</button>
                </div>
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
                  <td className="admin-mono admin-nowrap">
                    {editingRule === r.key ? (
                      <span className="admin-edit">
                        {r.comparator === 'GT' ? '>' : '<'}{' '}
                        <input
                          className="admin-input admin-input--sm" type="number" step="any" autoFocus
                          value={editValue}
                          onChange={(e) => setEditValue(e.target.value)}
                          onBlur={() => commitEdit(r)}
                          onKeyDown={(e) => { if (e.key === 'Enter') commitEdit(r); if (e.key === 'Escape') setEditingRule(null) }}
                        />
                        {r.unit === 'PERCENT' && '%'}
                      </span>
                    ) : (
                      <button className="admin-editbtn" onClick={() => beginEdit(r)} title={t('admin.rules.editHint')}>
                        {r.comparator === 'GT' ? '>' : '<'} {formatValue(r.threshold, r.unit)} <span className="admin-editbtn__pen" aria-hidden>✎</span>
                      </button>
                    )}
                  </td>
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
          <h3 className="admin-card__title"><span aria-hidden>📡</span> {t('admin.registry.title', { n: targets.length })}</h3>
          <table className="admin-table">
            <thead>
              <tr>
                <th>{t('admin.registry.state')}</th>
                <th>{t('admin.registry.name')}</th><th>{t('admin.registry.instance')}</th>
                <th>URL</th><th>IP</th>
                <th>{t('admin.registry.source')}</th><th>{t('admin.registry.heartbeat')}</th>
                <th className="admin-table__actions" />
              </tr>
            </thead>
            <tbody>
              {targets.map((tg) => (
                <tr key={tg.id}>
                  <td className="admin-nowrap"><span className={`admin-dot ${STATE_DOT[tg.state]}`} aria-hidden>●</span> <span className="admin-small">{tg.state}</span></td>
                  <td>{tg.name}</td>
                  <td className="admin-mono">{tg.instance ?? '—'}</td>
                  <td className="admin-mono admin-ellipsis" title={tg.url}>{tg.url}</td>
                  <td className="admin-mono">{tg.ip ?? '—'}</td>
                  <td><span className={`admin-chip admin-chip--${tg.source.toLowerCase()}`}>{t(`admin.source.${tg.source.toLowerCase()}`)}</span></td>
                  <td className="admin-nowrap">{tg.lastSeenMillis != null ? `${humanMillis(now - tg.lastSeenMillis)} ${t('admin.ago')}` : '—'}</td>
                  <td className="admin-table__actions">
                    {tg.source !== 'STATIC' && (
                      <button className="admin-iconbtn admin-iconbtn--danger" onClick={() => onRemoveTarget(tg)} title={t('admin.registry.remove')} aria-label={t('admin.registry.remove')}>🗑</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        {/* ---- Collector ---- */}
        <section className="admin-card admin-card--wide">
          <h3 className="admin-card__title"><span aria-hidden>🛠</span> {t('admin.collector.title')}</h3>
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
