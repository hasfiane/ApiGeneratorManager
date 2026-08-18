import { useLanguage } from '../i18n/LanguageProvider'

export function BetaLine({ compact = false }: { readonly compact?: boolean }) {
  const { text } = useLanguage()

  return (
    <div className={compact ? 'betaLine betaLineCompact' : 'betaLine'}>
      <span className="badge warn">{text.shell.betaBadge}</span>
      <span>{compact ? text.shell.betaLineCompact : text.shell.betaLine}</span>
    </div>
  )
}
