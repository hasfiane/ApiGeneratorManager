import { lazy, Suspense } from 'react'
import type { ReactNode } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from '../state/auth'
import { RequireAuth } from './RequireAuth'
import { ErrorBoundary } from '../components/ErrorBoundary'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { LanguageProvider, useLanguage } from '../i18n/LanguageProvider'

const Login = lazy(() => import('../pages/Login'))
const VerifyEmail = lazy(() => import('../pages/VerifyEmail'))
const OAuth2Callback = lazy(() => import('../pages/OAuth2Callback'))
const Overview = lazy(() => import('../pages/Overview'))
const Generators = lazy(() => import('../pages/Generators'))
const Docs = lazy(() => import('../pages/Docs'))
const AdminDashboard = lazy(() => import('../pages/AdminDashboard'))
const DbConsole = lazy(() => import('../pages/DbConsole'))
const Security = lazy(() => import('../pages/Security'))

function ProtectedPage({ children }: { readonly children: ReactNode }) {
  return <RequireAuth><ErrorBoundary>{children}</ErrorBoundary></RequireAuth>
}

function AdminPage({ children }: { readonly children: ReactNode }) {
  return <RequireAuth><AdminOnly><ErrorBoundary>{children}</ErrorBoundary></AdminOnly></RequireAuth>
}

function AdminOnly({ children }: { readonly children: ReactNode }) {
  const { ready, isAdmin } = useAuth()
  const { text } = useLanguage()
  if (!ready) return <LoadingSpinner message={text.shell.checkingAuth} />
  if (!isAdmin) return <Navigate to="/app" replace />
  return <>{children}</>
}

function AppRoutes() {
  const { text } = useLanguage()
  return (
    <AuthProvider>
      <Suspense fallback={<LoadingSpinner message={text.shell.loadingPage} />}>
        <Routes>
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Login />} />
          <Route path="/forgot-password" element={<Login />} />
          <Route path="/reset-password" element={<Login />} />
          <Route path="/verify-email" element={<VerifyEmail />} />
          <Route path="/oauth2/callback" element={<OAuth2Callback />} />
          <Route path="/app" element={<ProtectedPage><Overview /></ProtectedPage>} />
          <Route path="/app/generators" element={<ProtectedPage><Generators /></ProtectedPage>} />
          <Route path="/app/databases" element={<Navigate to="/app/generators" replace />} />
          <Route path="/app/security" element={<ProtectedPage><Security /></ProtectedPage>} />
          <Route path="/app/billing" element={<Navigate to="/app" replace />} />
          <Route path="/app/docs" element={<ProtectedPage><Docs /></ProtectedPage>} />
          <Route path="/app/admin" element={<AdminPage><AdminDashboard /></AdminPage>} />
          <Route path="/app/admin/database" element={<AdminPage><DbConsole /></AdminPage>} />
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </Suspense>
    </AuthProvider>
  )
}

export default function App() {
  return (
    <ErrorBoundary>
      <LanguageProvider><AppRoutes /></LanguageProvider>
    </ErrorBoundary>
  )
}
