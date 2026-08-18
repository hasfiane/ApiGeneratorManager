import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Shell } from '../components/Shell'
import { ApiError, api, type AdminDashboard, type AdminSecretRotation } from '../services/api'
import { useAuth } from '../state/auth'
import { useLanguage } from '../i18n/LanguageProvider'

const REFRESH_INTERVAL_MS = 5000

function formatDate(value?: string | null) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}

function statusBadgeClass(status?: string | null) {
  const normalized = (status ?? '').toUpperCase()
  if (['DONE', 'SUCCEEDED', 'DEPLOYED', 'RUNNING'].includes(normalized)) return 'badge good'
  if (['FAILED'].includes(normalized)) return 'badge bad'
  return 'badge warn'
}

function httpStatusBadgeClass(status: number) {
  if (status >= 500) return 'badge bad'
  if (status >= 400) return 'badge warn'
  return 'badge good'
}

function truncate(value?: string | null, max = 96) {
  if (!value) return '-'
  return value.length > max ? `${value.slice(0, max)}...` : value
}

function StatusPanel({ title, text, rows }: { readonly title: string; readonly text: string; readonly rows: [string, number][] }) {
  return (
    <section className="card">
      <div className="panelHeader">
        <div>
          <h3 className="panelTitle">{title}</h3>
          <p className="panelText">{text}</p>
        </div>
      </div>
      <div className="adminStatusList">
        {rows.map(([status, count]) => (
          <div className="adminStatusRow" key={status}>
            <span className={statusBadgeClass(status)}>{status}</span>
            <strong>{count}</strong>
          </div>
        ))}
      </div>
    </section>
  )
}

export default function AdminDashboardPage() {
  const { text } = useLanguage()
  const { isAdmin, ready } = useAuth()
  const t = text.admin
  const [dashboard, setDashboard] = useState<AdminDashboard | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [rotatingSecret, setRotatingSecret] = useState(false)
  const [rotatedSecret, setRotatedSecret] = useState<AdminSecretRotation | null>(null)

  const load = useCallback(async (showLoader = false, isCurrent: () => boolean = () => true) => {
    if (!ready || !isCurrent()) return
    if (!isAdmin) {
      if (!isCurrent()) return
      setDashboard(null)
      setError(t.forbidden)
      setLoading(false)
      setRefreshing(false)
      return
    }

    if (showLoader && isCurrent()) setRefreshing(true)
    try {
      const next = await api.getAdminDashboard(12)
      if (!isCurrent()) return
      setDashboard(next)
      setError(null)
    } catch (err) {
      if (!isCurrent()) return
      const message = err instanceof ApiError && err.isForbidden ? t.forbidden : err instanceof Error ? err.message : t.loadError
      setError(message)
    } finally {
      if (isCurrent()) {
        setLoading(false)
        setRefreshing(false)
      }
    }
  }, [isAdmin, ready, t.forbidden, t.loadError])

  useEffect(() => {
    let active = true
    const safeLoad = async (showLoader = false) => {
      await load(showLoader, () => active)
    }

    void safeLoad()
    const intervalId = window.setInterval(() => { void safeLoad() }, REFRESH_INTERVAL_MS)
    return () => {
      active = false
      window.clearInterval(intervalId)
    }
  }, [load])

  const rotateAdminSecret = useCallback(async () => {
    if (!isAdmin || rotatingSecret) return
    if (!window.confirm(t.rotateConfirm)) return
    setRotatingSecret(true)
    setError(null)
    try {
      const rotated = await api.rotateAdminPassword()
      setRotatedSecret(rotated)
    } catch (err) {
      const message = err instanceof ApiError && err.isForbidden ? t.forbidden : err instanceof Error ? err.message : t.rotateError
      setError(message)
    } finally {
      setRotatingSecret(false)
    }
  }, [isAdmin, rotatingSecret, t.forbidden, t.rotateConfirm, t.rotateError])

  const metrics = useMemo(() => {
    const summary = dashboard?.summary
    return [
      [t.metrics.users, String(summary?.totalUsers ?? 0), t.metrics.usersHint],
      [t.metrics.apis, String(summary?.totalGeneratedApis ?? 0), t.metrics.apisHint.replace('{count}', String(summary?.successfulGenerations ?? 0))],
      [t.metrics.activeJobs, String(summary?.activeJobs ?? 0), t.metrics.activeJobsHint],
      [t.metrics.attempts, String(summary?.generationAttempts ?? 0), t.metrics.attemptsHint.replace('{count}', String(summary?.attemptsLast24h ?? 0))],
      [t.metrics.errors, String((summary?.failedGenerations ?? 0) + (summary?.failedPreviews ?? 0)), t.metrics.errorsHint.replace('{rate}', String(summary?.failureRate ?? 0))],
      [t.metrics.preview, String(summary?.activePreviews ?? 0), t.metrics.previewHint.replace('{count}', String(summary?.totalPreviews ?? 0))],
      [t.metrics.successRate, `${summary?.successRate ?? 0}%`, t.metrics.successRateHint.replace('{seconds}', String(summary?.averageGenerationSeconds ?? 0))],
      [t.metrics.last24h, String(summary?.apisCreatedLast24h ?? 0), t.metrics.last24hHint.replace('{previews}', String(summary?.previewsCreatedLast24h ?? 0))],
    ]
  }, [dashboard, t.metrics])

  const generationStatusRows = Object.entries(dashboard?.generationStatuses ?? {})
  const jobStatusRows = Object.entries(dashboard?.jobStatuses ?? {})
  const previewStatusRows = Object.entries(dashboard?.previewStatuses ?? {})
  const lastRefresh = dashboard?.summary.generatedAt ? formatDate(dashboard.summary.generatedAt) : '-'

  return (
    <Shell
      title={t.title}
      subtitle={t.subtitle}
      actions={(
        <>
          <button className="btn" type="button" disabled={refreshing || !isAdmin} onClick={() => { void load(true) }}>
            {refreshing ? t.refreshing : t.refresh}
          </button>
          <button className="btn danger" type="button" disabled={rotatingSecret || !isAdmin} onClick={() => { void rotateAdminSecret() }}>
            {rotatingSecret ? t.rotatingSecret : t.rotateSecret}
          </button>
          <Link className="btn primary" to="/app/generators">{t.openGenerators}</Link>
        </>
      )}
    >
      {!isAdmin && <div className="callout"><strong>{t.adminOnly}</strong><p className="panelText">{t.adminOnlyText}</p></div>}
      {error && <div className="callout errorCallout"><strong>{t.errorTitle}</strong><p className="panelText">{error}</p></div>}

      <div className="adminOpsBar">
        <span className="pill">{t.live}</span>
        <span>{t.lastRefresh}: <strong>{lastRefresh}</strong></span>
        <span>{t.window}: <strong>24h</strong></span>
      </div>

      {rotatedSecret ? (
        <section className="card adminSecretCard">
          <div>
            <h3 className="panelTitle">{t.rotatedSecretTitle}</h3>
            <p className="panelText">{t.rotatedSecretText.replace('{email}', rotatedSecret.email)}</p>
            <p className="codeInline adminSecretValue">{rotatedSecret.temporaryPassword}</p>
            <small>{t.rotatedAt}: {formatDate(rotatedSecret.rotatedAt)}</small>
          </div>
          <button
            className="btn"
            type="button"
            onClick={() => { void navigator.clipboard?.writeText(rotatedSecret.temporaryPassword) }}
          >
            {t.copySecret}
          </button>
        </section>
      ) : null}

      {dashboard?.databaseTool ? (
        <section className="card adminDatabaseTool">
          <div>
            <h3 className="panelTitle">{t.databaseTitle}</h3>
            <p className="panelText">{dashboard.databaseTool.warning ?? t.databaseText}</p>
            <p className="codeInline adminDatabaseToolUrl">{dashboard.databaseTool.url}</p>
          </div>
          <a
            className={dashboard.databaseTool.enabled ? 'btn primary' : 'btn'}
            href={dashboard.databaseTool.enabled ? dashboard.databaseTool.url : undefined}
            target="_blank"
            rel="noreferrer"
            aria-disabled={!dashboard.databaseTool.enabled}
          >
            {t.openDatabase}
          </a>
        </section>
      ) : null}

      <div className="grid cols-3 adminMetricGrid">
        {metrics.map(([label, value, hint]) => (
          <div className="card metric" key={label}>
            <div className="label">{label}</div>
            <strong>{loading ? t.loading : value}</strong>
            <span>{hint}</span>
          </div>
        ))}
      </div>

      <div className="grid cols-3 adminDashboardGrid" style={{ marginTop: 16 }}>
        <StatusPanel title={t.generationStatusTitle} text={t.generationStatusText} rows={generationStatusRows} />
        <StatusPanel title={t.jobStatusTitle} text={t.jobStatusText} rows={jobStatusRows} />
        <StatusPanel title={t.previewStatusTitle} text={t.previewStatusText} rows={previewStatusRows} />
      </div>

      <section className="card" style={{ marginTop: 16 }}>
        <div className="panelHeader">
          <div>
            <h3 className="panelTitle">{t.errorsTitle}</h3>
            <p className="panelText">{t.errorsText}</p>
          </div>
          <span className="badge bad">{dashboard?.errors.length ?? 0}</span>
        </div>
        <div className="adminErrorList">
          {dashboard?.errors.length ? dashboard.errors.map((item) => (
            <article className="adminErrorItem" key={`${item.source}-${item.generatedApiId}-${item.occurredAt}-${item.code}`}>
              <div>
                <span className="badge bad">{item.source}{item.code ? ` · ${item.code}` : ''}</span>
                <h4>{item.generatedApiName ?? '-'}</h4>
                <p>{truncate(item.hint ?? item.message, 140)}</p>
              </div>
              <small>{item.ownerEmail ?? '-'} · {formatDate(item.occurredAt)}</small>
            </article>
          )) : <p className="muted">{loading ? t.loading : t.noErrors}</p>}
        </div>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <div className="panelHeader">
          <div>
            <h3 className="panelTitle">{t.recentApisTitle}</h3>
            <p className="panelText">{t.recentApisText}</p>
          </div>
        </div>
        <div className="tableWrap">
          <table className="table">
            <thead>
              <tr>
                <th>{t.table.api}</th>
                <th>{t.table.owner}</th>
                <th>{t.table.status}</th>
                <th>{t.table.progress}</th>
                <th>{t.table.job}</th>
                <th>{t.table.created}</th>
                <th>{t.table.error}</th>
              </tr>
            </thead>
            <tbody>
              {dashboard?.recentApis.length ? dashboard.recentApis.map((item) => (
                <tr key={item.id}>
                  <td><strong>{item.name}</strong><br /><span className="muted">{item.dbType ?? '-'}</span></td>
                  <td>{item.ownerEmail}</td>
                  <td><span className={statusBadgeClass(item.status)}>{item.status ?? '-'}</span></td>
                  <td>{item.progress ?? 0}%</td>
                  <td><span className={statusBadgeClass(item.jobStatus)}>{item.jobStatus ?? item.jobId ?? '-'}</span></td>
                  <td>{formatDate(item.createdAt)}</td>
                  <td>{truncate(item.errorMessage, 80)}</td>
                </tr>
              )) : <tr><td colSpan={7}>{loading ? t.loading : t.empty}</td></tr>}
            </tbody>
          </table>
        </div>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <div className="panelHeader">
          <div>
            <h3 className="panelTitle">{t.attemptsTitle}</h3>
            <p className="panelText">{t.attemptsText}</p>
          </div>
        </div>
        <div className="tableWrap">
          <table className="table">
            <thead>
              <tr>
                <th>{t.table.job}</th>
                <th>{t.table.api}</th>
                <th>{t.table.owner}</th>
                <th>{t.table.status}</th>
                <th>{t.table.options}</th>
                <th>{t.table.updated}</th>
                <th>{t.table.error}</th>
              </tr>
            </thead>
            <tbody>
              {dashboard?.jobAttempts.length ? dashboard.jobAttempts.map((item) => (
                <tr key={item.jobId}>
                  <td><span className="codeInline">{item.jobId}</span></td>
                  <td>{item.generatedApiName ?? '-'}</td>
                  <td>{item.ownerEmail ?? '-'}</td>
                  <td><span className={statusBadgeClass(item.status)}>{item.status ?? '-'}</span></td>
                  <td>{item.buildRequested ? t.build : t.noBuild} · {item.deployDockerRequested ? t.docker : t.noDocker}</td>
                  <td>{formatDate(item.updatedAt)}</td>
                  <td>{truncate(item.errorMessage, 80)}</td>
                </tr>
              )) : <tr><td colSpan={7}>{loading ? t.loading : t.empty}</td></tr>}
            </tbody>
          </table>
        </div>
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <div className="panelHeader">
          <div>
            <h3 className="panelTitle">{t.apiCallsTitle}</h3>
            <p className="panelText">{t.apiCallsText}</p>
          </div>
        </div>
        <div className="tableWrap">
          <table className="table">
            <thead>
              <tr>
                <th>{t.table.created}</th>
                <th>{t.table.method}</th>
                <th>{t.table.path}</th>
                <th>{t.table.status}</th>
                <th>{t.table.duration}</th>
                <th>{t.table.principal}</th>
                <th>{t.table.trace}</th>
              </tr>
            </thead>
            <tbody>
              {dashboard?.recentApiCalls.length ? dashboard.recentApiCalls.map((item) => (
                <tr key={`${item.traceId}-${item.timestamp}-${item.path}`}>
                  <td>{formatDate(item.timestamp)}</td>
                  <td><span className="badge">{item.method}</span></td>
                  <td><span className="codeInline">{item.path}</span></td>
                  <td><span className={httpStatusBadgeClass(item.status)}>{item.status}</span></td>
                  <td>{item.durationMs}ms</td>
                  <td>{item.principal ?? item.clientIp ?? '-'}</td>
                  <td><span className="codeInline">{item.traceId ?? '-'}</span></td>
                </tr>
              )) : <tr><td colSpan={7}>{loading ? t.loading : t.empty}</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
    </Shell>
  )
}
