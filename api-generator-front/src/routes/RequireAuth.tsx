import React, { useEffect, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { useLanguage } from '../i18n/LanguageProvider'
import { useAuth } from '../state/auth'

interface Props {
  readonly children: React.ReactNode
}

export function RequireAuth({ children }: Props) {
  const { ready, verifySession } = useAuth()
  const loc = useLocation()
  const { text } = useLanguage()
  const [status, setStatus] = useState<'checking' | 'allowed' | 'denied'>('checking')

  useEffect(() => {
    if (!ready) return

    let active = true
    setStatus('checking')

    void verifySession()
      .then(() => {
        if (active) setStatus('allowed')
      })
      .catch(() => {
        if (active) setStatus('denied')
      })

    return () => { active = false }
  }, [loc.pathname, ready, verifySession])

  if (!ready) return <LoadingSpinner message={text.shell.checkingAuth} />

  if (status === 'denied') {
    return <Navigate to="/login" replace state={{ from: loc.pathname }} />
  }

  if (status !== 'allowed') return <LoadingSpinner message={text.shell.checkingAuth} />

  return <>{children}</>
}
