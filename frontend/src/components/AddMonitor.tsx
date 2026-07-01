import { useState } from 'react'
import { addHttpMonitor, ApiError } from '../api'
import { useT } from '../i18n'

/** Wall control: add a plain HTTP liveness monitor (name + URL) via POST /api/targets/http.
 *  Collapsed to a single button until opened; server {code} errors are localized inline. */
export function AddMonitor({ onAdded }: { onAdded: () => void }) {
  const t = useT()
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [url, setUrl] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const close = () => { setOpen(false); setName(''); setUrl(''); setErr(null); setBusy(false) }

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true); setErr(null)
    addHttpMonitor(name.trim(), url.trim())
      .then(() => { onAdded(); close() })
      .catch((e2) => {
        const code = e2 instanceof ApiError ? e2.code : 'ERROR'
        setErr(
          code === 'TARGET_NAME_TAKEN' ? t('addmon.errTaken')
          : code === 'INVALID_REQUEST' ? t('addmon.errInvalid')
          : t('addmon.errGeneric'),
        )
        setBusy(false)
      })
  }

  if (!open) {
    return <button type="button" className="addmon__toggle" onClick={() => setOpen(true)}>{t('addmon.add')}</button>
  }
  return (
    <form className="addmon" onSubmit={submit}>
      <input className="addmon__input" value={name} onChange={(e) => setName(e.target.value)}
        placeholder={t('addmon.name')} aria-label={t('addmon.name')} autoFocus />
      <input className="addmon__input addmon__input--url" value={url} onChange={(e) => setUrl(e.target.value)}
        placeholder={t('addmon.url')} aria-label="URL" />
      <button type="submit" className="addmon__submit" disabled={busy}>{t('addmon.submit')}</button>
      <button type="button" className="addmon__cancel" onClick={close}>{t('addmon.cancel')}</button>
      {err ? <span className="addmon__err">{err}</span> : <span className="addmon__hint">{t('addmon.hint')}</span>}
    </form>
  )
}
