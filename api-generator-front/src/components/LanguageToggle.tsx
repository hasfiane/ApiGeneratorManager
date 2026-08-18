import { useLanguage } from '../i18n/LanguageProvider'

function FlagFr() {
  return (
    <svg viewBox="0 0 3 2" aria-hidden="true">
      <rect width="1" height="2" x="0" fill="#0055a4" />
      <rect width="1" height="2" x="1" fill="#ffffff" />
      <rect width="1" height="2" x="2" fill="#ef4135" />
    </svg>
  )
}

function FlagGb() {
  return (
    <svg viewBox="0 0 60 30" aria-hidden="true">
      <clipPath id="uk-clip">
        <path d="M0,0 v30 h60 v-30 z" />
      </clipPath>
      <path d="M0,0 v30 h60 v-30 z" fill="#012169" />
      <path d="M0,0 60,30 M60,0 0,30" stroke="#fff" strokeWidth="6" clipPath="url(#uk-clip)" />
      <path d="M0,0 60,30 M60,0 0,30" stroke="#c8102e" strokeWidth="4" clipPath="url(#uk-clip)" />
      <path d="M30,0 v30 M0,15 h60" stroke="#fff" strokeWidth="10" />
      <path d="M30,0 v30 M0,15 h60" stroke="#c8102e" strokeWidth="6" />
    </svg>
  )
}

export function LanguageToggle({
  className = 'languageToggle',
  variant = 'flags',
}: {
  readonly className?: string
  readonly variant?: 'flags' | 'compact'
}) {
  const { locale, text, toggleLanguage } = useLanguage()
  const currentFlag = locale === 'fr' ? <FlagFr /> : <FlagGb />
  const nextFlag = locale === 'fr' ? <FlagGb /> : <FlagFr />
  const currentLabel = locale === 'fr' ? 'Français' : 'English'
  const nextLabel = locale === 'fr' ? 'Anglais' : 'French'
  const ariaLabel = `${text.shell.languageToggle}: ${currentLabel} / ${nextLabel}`

  return (
    <button
      className={className}
      type="button"
      aria-label={ariaLabel}
      title={ariaLabel}
      onClick={toggleLanguage}
    >
      {variant === 'compact' ? (
        <>
          <svg className="languageGlobe" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="9" />
            <path d="M3 12h18M12 3c2.3 2.4 3.5 5.4 3.5 9S14.3 18.6 12 21M12 3c-2.3 2.4-3.5 5.4-3.5 9S9.7 18.6 12 21" />
          </svg>
          <span className="languageCode">{locale === 'fr' ? 'FR' : 'EN'}</span>
        </>
      ) : (
        <>
          <span className="flagIcon">{currentFlag}</span>
          <span className="flagIcon">{nextFlag}</span>
        </>
      )}
    </button>
  )
}
