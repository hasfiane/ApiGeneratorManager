import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { BrandLogo } from '../components/BrandLogo'
import { BetaLine } from '../components/BetaLine'
import { LanguageToggle } from '../components/LanguageToggle'
import { api, ApiError } from '../services/api'
import { useLanguage } from '../i18n/LanguageProvider'
import { useAuth } from '../state/auth'

type AuthMode = 'login' | 'register' | 'forgot' | 'reset'

function modeFromPath(pathname: string): AuthMode {
  if (pathname === '/register') return 'register'
  if (pathname === '/forgot-password') return 'forgot'
  if (pathname === '/reset-password') return 'reset'
  return 'login'
}

export default function Login() {
  const nav = useNavigate()
  const loc = useLocation()
  const auth = useAuth()
  const { text } = useLanguage()
  const t = text.login

  const [mode, setMode] = useState<AuthMode>(() => modeFromPath(loc.pathname))
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [googleEnabled, setGoogleEnabled] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [resendingVerification, setResendingVerification] = useState(false)

  const state = loc.state as { from?: string; error?: string; info?: string } | null
  const from = state?.from ?? '/app'
  const googleUrl = useMemo(() => api.googleAuthUrl(), [])

  useEffect(() => {
    if (typeof window === 'undefined') return
    document.documentElement.dataset.theme = window.localStorage.getItem('ui-theme') === 'dark' ? 'dark' : 'light'
  }, [])

  useEffect(() => {
    if (auth.ready && auth.isAuthenticated) {
      nav(from, { replace: true })
    }
  }, [auth.isAuthenticated, auth.ready, from, nav])

  useEffect(() => {
    setMode(modeFromPath(loc.pathname))
    setError(state?.error ?? null)
    setInfo(state?.info ?? null)
  }, [loc.pathname, state?.error, state?.info])

  useEffect(() => {
    let active = true
    void api.oauth2Status()
      .then((status) => {
        if (active) setGoogleEnabled(status.googleEnabled)
      })
      .catch(() => {
        if (active) setGoogleEnabled(false)
      })
    return () => { active = false }
  }, [])

  function go(nextMode: AuthMode) {
    const path = nextMode === 'register' ? '/register' : nextMode === 'forgot' ? '/forgot-password' : nextMode === 'reset' ? '/reset-password' : '/login'
    nav(path, { replace: true, state: { from } })
  }

  function mapAuthError(err: unknown): string {
    if (!(err instanceof ApiError)) return t.unexpected
    if (err.status === 0) return text.errors.network
    if (err.message === 'Unable to initialize CSRF protection.') return text.errors.csrf
    if (err.status === 401) {
      return mode === 'login'
        ? t.invalid
        : (err.message && err.message.trim() ? err.message : t.failed)
    }
    if (err.status === 403) {
      return err.message.toLowerCase().includes('email not verified')
        ? t.emailNotVerified
        : text.errors.forbidden
    }
    if (err.status === 429) return t.rateLimited
    if (err.status === 503 && err.message.toLowerCase().includes('verification email')) return t.verificationEmailUnavailable
    if (err.status === 404 && mode === 'forgot') return t.recoveryUnavailable

    const raw = err.message.toLowerCase()
    if (raw.includes('email already exists')) return t.emailExists
    if (raw.includes('size') || raw.includes('password')) return t.passwordTooShort
    if (raw.includes('missing credentials')) return t.missingCredentials
    if (raw.includes('invalid or expired reset code')) return t.resetCodeInvalid

    return err.message || t.failed
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setInfo(null)

    const login = identifier.trim()
    if (!login) {
      setError(t.missingIdentifier)
      return
    }

    if (mode !== 'forgot' && !password) {
      setError(t.missingCredentials)
      return
    }

    if ((mode === 'register' || mode === 'reset') && password.length < 8) {
      setError(t.passwordTooShort)
      return
    }

    setLoading(true)
    try {
      if (mode === 'login') {
        await auth.login(login, password)
        nav(from, { replace: true })
      } else if (mode === 'register') {
        await auth.register(login, password)
        setPassword('')
        nav('/login', { replace: true, state: { from, info: t.verifyEmailSent } })
      } else if (mode === 'reset') {
        await api.resetPassword(login, password)
        setPassword('')
        nav('/login', { replace: true, state: { from, info: t.passwordResetDone } })
      } else {
        await api.requestPasswordReset(login)
        setInfo(t.resetEmailSent)
      }
    } catch (err) {
      setError(mapAuthError(err))
    } finally {
      setLoading(false)
    }
  }

  async function resendVerification() {
    const email = identifier.trim()
    if (!email) {
      setError(t.missingIdentifier)
      return
    }

    setResendingVerification(true)
    setError(null)
    setInfo(null)
    try {
      await api.resendVerificationEmail(email)
      setInfo(t.verifyEmailResent)
    } catch (err) {
      setError(mapAuthError(err))
    } finally {
      setResendingVerification(false)
    }
  }

  return (
    <div className="authPageSolo">
      <div className="authSoloCard">
        <div className="authSoloTop">
          <div className="authSoloBrand">
            <BrandLogo className="brandLogo brandLogo--login authSoloMark" />
            <div>
              <div className="authSoloName">{t.brand}</div>
              <div className="authSoloTag">{t.tag}</div>
            </div>
          </div>

          <LanguageToggle className="figmaLanguageButton" variant="compact" />
        </div>

        <div className="authSoloHead">
          <div className="authSoloTitle">
            {mode === 'login' ? t.loginTitle : mode === 'register' ? t.registerTitle : mode === 'forgot' ? t.forgotTitle : t.resetTitle}
          </div>
          <div className="authSoloSub">
            {mode === 'login' ? t.loginSubtitle : mode === 'register' ? t.registerSubtitle : mode === 'forgot' ? t.forgotSubtitle : t.resetSubtitle}
          </div>
        </div>

        <BetaLine compact />
        <div style={{ height: 16 }} />

        {mode !== 'forgot' && googleEnabled ? (
          <>
            <a className="googleBtn" href={googleUrl}>
              <svg className="googleIcon" width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
                <path fill="#EA4335" d="M24 9.5c3.2 0 6 1.1 8.2 3.1l6.1-6.1C34.6 3.1 29.7 1 24 1 14.6 1 6.6 6.4 2.7 14.2l7.4 5.8C12 13.7 17.5 9.5 24 9.5z" />
                <path fill="#34A853" d="M46.1 24.5c0-1.6-.1-2.8-.4-4.2H24v8h12.5c-.5 2.7-2 5-4.2 6.6l6.4 5c3.7-3.4 5.4-8.4 5.4-15.4z" />
                <path fill="#4A90E2" d="M10.1 28.1c-.6-1.7-1-3.5-1-5.6s.4-3.9 1-5.6l-7.4-5.8C1.2 14.6 0 18.2 0 22.5s1.2 7.9 2.7 11.4l7.4-5.8z" />
                <path fill="#FBBC05" d="M24 46c5.7 0 10.5-1.9 14-5.1l-6.4-5c-1.8 1.2-4.2 2-7.6 2-6.5 0-12-4.2-13.9-10.2l-7.4 5.8C6.6 41.6 14.6 46 24 46z" />
              </svg>
              {t.google}
            </a>

            <div className="sep">
              <span />
              <div>{t.separator}</div>
              <span />
            </div>
          </>
        ) : null}

        <form onSubmit={onSubmit} className="authForm">
          <label className="field">
            <span>{t.identifier}</span>
            <input
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              autoComplete={mode === 'reset' ? 'one-time-code' : 'username'}
              inputMode={mode === 'register' || mode === 'forgot' ? 'email' : 'text'}
              placeholder={mode === 'reset' ? t.resetCodePlaceholder : t.identifierPlaceholder}
              required
            />
          </label>

          {mode !== 'forgot' ? (
            <label className="field">
              <span>{t.password}</span>
              <input
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                type="password"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                placeholder={t.passwordPlaceholder}
                minLength={mode === 'register' || mode === 'reset' ? 8 : undefined}
                required
              />
            </label>
          ) : null}

          {error ? <div className="errorBox">{error}</div> : null}
          {error === t.emailNotVerified && identifier.trim().includes('@') ? (
            <button
              type="button"
              className="linkBtn"
              onClick={resendVerification}
              disabled={loading || resendingVerification}
            >
              {resendingVerification ? t.loading : t.resendVerification}
            </button>
          ) : null}
          {info ? <div className="infoBox">{info}</div> : null}

          <button className="primaryBtn" disabled={loading}>
            {loading ? t.loading : mode === 'login' ? t.loginCta : mode === 'register' ? t.registerCta : mode === 'forgot' ? t.forgotCta : t.resetCta}
          </button>

          <div className="switchRow">
            {mode === 'login' ? (
              <>
                <span>{t.noAccount}</span>
                <button type="button" className="linkBtn" onClick={() => go('register')} disabled={loading}>
                  {t.createAccount}
                </button>
                <button type="button" className="linkBtn" onClick={() => go('forgot')} disabled={loading}>
                  {t.forgotPassword}
                </button>
              </>
            ) : (
              <>
                <span>{mode === 'register' ? t.hasAccount : mode === 'forgot' ? t.recoveryInfo : t.hasResetCode}</span>
                {mode === 'forgot' ? (
                  <button type="button" className="linkBtn" onClick={() => go('reset')} disabled={loading}>
                    {t.resetWithCode}
                  </button>
                ) : null}
                <button type="button" className="linkBtn" onClick={() => go('login')} disabled={loading}>
                  {t.backToLogin}
                </button>
              </>
            )}
          </div>
          <a className="authHomeLink" href="/">{t.backToHome}</a>
        </form>
      </div>
    </div>
  )
}
