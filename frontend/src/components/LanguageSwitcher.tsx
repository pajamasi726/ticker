import { LANGS, useLang, setLang } from '../i18n'
import type { Lang } from '../i18n'

const LABEL: Record<Lang, string> = { en: 'EN', ko: '한국어' }

/** Compact segmented-button switcher shown fixed top-right on every view. */
export function LanguageSwitcher() {
  const lang = useLang()
  return (
    <div className="lang-switch" role="group" aria-label="Language">
      {LANGS.map((l) => (
        <button
          key={l}
          className={lang === l ? 'on' : ''}
          onClick={() => setLang(l)}
          aria-pressed={lang === l}
        >
          {LABEL[l]}
        </button>
      ))}
    </div>
  )
}
