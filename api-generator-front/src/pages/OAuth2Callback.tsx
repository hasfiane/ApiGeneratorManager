import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { useLanguage } from '../i18n/LanguageProvider'
import { useAuth } from '../state/auth'

export default function OAuth2Callback() {
  const nav = useNavigate()
  const { hydrateFromSession } = useAuth()
  const { text } = useLanguage()

  useEffect(() => {
    void hydrateFromSession()
      .then(() => nav('/app', { replace: true }))
      .catch(() => nav('/login', { replace: true, state: { error: text.errors.oauthSession } }))
  }, [hydrateFromSession, nav, text.errors.oauthSession])

  return <LoadingSpinner message={text.shell.oauthFinalizing} />
}
