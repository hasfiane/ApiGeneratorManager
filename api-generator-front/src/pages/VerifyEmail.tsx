import { FormEvent, useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { BetaLine } from '../components/BetaLine'
import { api, ApiError } from '../services/api'
import { useLanguage } from '../i18n/LanguageProvider'

type Status = 'checking' | 'success' | 'error'

export default function VerifyEmail() {
  const { text } = useLanguage()
  const t = text.verifyEmail
  const nav = useNavigate()
  const [searchParams] = useSearchParams()
  const [status, setStatus] = useState<Status>('error')
  const [message, setMessage] = useState(t.missing)
  const [code, setCode] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const verifyToken = useCallback((value: string) => {
    setSubmitting(true)
    setStatus('checking')
    setMessage(t.checking)
    void api.verifyEmail(value)
      .then(() => {
        setStatus('success')
        setMessage(t.success)
      })
      .catch((err) => {
        setStatus('error')
        if (err instanceof ApiError) {
          if (err.status === 0) {
            setMessage(text.errors.network)
            return
          }
          if (err.status === 429) {
            setMessage(t.rateLimited)
            return
          }
          setMessage(t.invalid)
          return
        }
        setMessage(text.errors.unexpected)
      })
      .finally(() => setSubmitting(false))
  }, [t.checking, t.invalid, t.rateLimited, t.success, text.errors.network, text.errors.unexpected])

  useEffect(() => {
    const token = searchParams.get('token')?.trim()
    if (!token) return

    setCode(token)
    nav('/verify-email', { replace: true })
    verifyToken(token)
  }, [nav, searchParams, verifyToken])

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const value = code.trim()
    if (!value) {
      setStatus('error')
      setMessage(t.missing)
      return
    }

    verifyToken(value)
  }

  return (
    <div className="authPageSolo">
      <div className="authSoloCard">
        <div className="authSoloHead">
          <div className="authSoloTitle">{t.title}</div>
          <div className="authSoloSub">{t.subtitle}</div>
        </div>

        <BetaLine compact />
        <div style={{ height: 16 }} />

        <div className={status === 'success' ? 'infoBox' : status === 'error' ? 'errorBox' : 'infoBox'}>
          {message}
        </div>

        {status !== 'success' && (
          <form className="authForm" onSubmit={submit}>
            <label className="field">
              <span>{t.codeLabel}</span>
              <input
                value={code}
                onChange={(event) => setCode(event.target.value)}
                placeholder={t.codePlaceholder}
                autoComplete="one-time-code"
              />
            </label>
            <button className="primaryBtn" type="submit" disabled={submitting}>
              {submitting ? t.checking : t.verify}
            </button>
          </form>
        )}

        <div style={{ height: 14 }} />
        <Link className="primaryBtn" to="/login">{t.login}</Link>
      </div>
    </div>
  )
}
