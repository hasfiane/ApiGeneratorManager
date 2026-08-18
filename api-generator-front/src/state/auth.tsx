/* eslint-disable react-refresh/only-export-components */
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { api, ApiError, type Quotas } from '../services/api'

type AuthState = {
  ready: boolean
  isAuthenticated: boolean
  email: string | null
  plan: string | null
  quotas: Quotas | null
  roles: string[]
  isAdmin: boolean
  error: ApiError | null
  clearError: () => void
  login: (_email: string, _password: string) => Promise<void>
  register: (_email: string, _password: string) => Promise<{ email?: string }>
  hydrateFromSession: () => Promise<void>
  verifySession: () => Promise<void>
  logout: () => Promise<void>
}

const Ctx = createContext<AuthState | null>(null)

function isPublicPath(pathname: string): boolean {
  return pathname === '/'
    || pathname === '/login'
    || pathname === '/register'
    || pathname === '/forgot-password'
    || pathname === '/reset-password'
    || pathname === '/verify-email'
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const location = useLocation()
  const [ready, setReady] = useState(false)
  const [email, setEmail] = useState<string | null>(null)
  const [plan, setPlan] = useState<string | null>(null)
  const [quotas, setQuotas] = useState<Quotas | null>(null)
  const [roles, setRoles] = useState<string[]>([])
  const [error, setError] = useState<ApiError | null>(null)

  useEffect(() => {
    let active = true
    async function init() {
      if (!isPublicPath(location.pathname)) {
        setReady(false)
      } else {
        setReady(true)
      }
      try {
        const me = await api.getMe()
        if (!active) return
        setEmail(me.email)
        setPlan(me.plan ?? 'FREE')
        setQuotas(me.quotas ?? null)
        setRoles(me.roles ?? [])
      } catch {
        if (!active) return
        setEmail(null)
        setPlan(null)
        setQuotas(null)
        setRoles([])
      } finally {
        if (active) setReady(true)
      }
    }

    void init()
    return () => { active = false }
  }, [location.pathname])

  const clearError = useCallback(() => setError(null), [])

  const login = useCallback(async (e: string, p: string) => {
    setError(null)
    try {
      const r = await api.login(e, p)
      const me = await api.getMe()
      setEmail(me.email ?? r.email ?? e)
      setPlan(me.plan ?? r.plan ?? 'FREE')
      setQuotas(me.quotas ?? null)
      setRoles(me.roles ?? [])
    } catch (err) {
      const apiErr = err instanceof ApiError
        ? err
        : new ApiError(0, 'Unable to sign in right now.')
      setError(apiErr)
      throw apiErr
    }
  }, [])

  const register = useCallback(async (e: string, p: string) => {
    setError(null)
    try {
      const r = await api.register(e, p)
      setEmail(null)
      setPlan(null)
      setQuotas(null)
      setRoles([])
      return { email: r.email ?? e }
    } catch (err) {
      const apiErr = err instanceof ApiError
        ? err
        : new ApiError(0, 'Unable to create the account right now.')
      setError(apiErr)
      throw apiErr
    }
  }, [])

  const hydrateFromSession = useCallback(async () => {
    const me = await api.getMe()
    setEmail(me.email)
    setPlan(me.plan)
    setQuotas(me.quotas ?? null)
    setRoles(me.roles ?? [])
    setError(null)
  }, [])

  const verifySession = useCallback(async () => {
    try {
      const me = await api.getMe()
      setEmail(me.email)
      setPlan(me.plan)
      setQuotas(me.quotas ?? null)
      setRoles(me.roles ?? [])
      setError(null)
    } catch (err) {
      setEmail(null)
      setPlan(null)
      setQuotas(null)
      setRoles([])
      setError(null)
      throw err
    }
  }, [])

  const logout = useCallback(async () => {
    await api.logout().catch(() => undefined)
    setEmail(null)
    setPlan(null)
    setQuotas(null)
    setRoles([])
    setError(null)
  }, [])

  const isAdmin = roles.includes('ROLE_ADMIN')

  const value = useMemo<AuthState>(() => ({
    ready,
    isAuthenticated: !!email,
    email,
    plan,
    quotas,
    roles,
    isAdmin,
    error,
    clearError,
    login,
    register,
    hydrateFromSession,
    verifySession,
    logout,
  }), [ready, email, plan, quotas, roles, isAdmin, error, clearError, login, register, hydrateFromSession, verifySession, logout])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(Ctx)
  if (!ctx) throw new Error('useAuth must be used inside an AuthProvider')
  return ctx
}
