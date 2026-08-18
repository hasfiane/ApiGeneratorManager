import { useEffect, useMemo, useState } from 'react'
import { Shell } from '../components/Shell'
import { useLanguage } from '../i18n/LanguageProvider'
import { api, type AccountSummary, type ApiProject, type FailedPreview } from '../services/api'

export default function Overview() {
  const { text } = useLanguage()
  const t = text.overview
  const [projects, setProjects] = useState<ApiProject[]>([])
  const [summary, setSummary] = useState<AccountSummary | null>(null)
  const [failedPreviews, setFailedPreviews] = useState<FailedPreview[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    void Promise.allSettled([api.getMyApis(), api.getAccountSummary(), api.getRecentFailedPreviews(5)])
      .then(([projectsResult, summaryResult, failedPreviewsResult]) => {
        if (!active) return
        const nextProjects = projectsResult.status === 'fulfilled' ? projectsResult.value : []
        setProjects(nextProjects)
        setSummary(summaryResult.status === 'fulfilled' ? summaryResult.value : null)
        setFailedPreviews(failedPreviewsResult.status === 'fulfilled' ? failedPreviewsResult.value : [])
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [])

  const metrics = useMemo(() => {
    const totalApis = summary?.totalGeneratedApis ?? projects.length
    const activePreviews = summary?.activePreviews ?? 0
    const latest = projects[0]?.createdAt ? new Date(projects[0].createdAt).toLocaleDateString() : t.noActivity
    const averageGeneration = summary ? t.durationFormat.replace('{seconds}', String(summary.averageGenerationSeconds)) : t.loading
    const averagePreviewStartup = summary ? t.durationFormat.replace('{seconds}', String(summary.averagePreviewStartupSeconds)) : t.loading
    const previewRuntime = summary ? t.durationFormat.replace('{seconds}', String(summary.averagePreviewRuntimeSeconds)) : t.loading
    return [
      [t.generatedApis, String(totalApis), loading ? t.loading : t.ownedByYou],
      [t.activePreviews, String(activePreviews), t.previewHint.replace('{count}', String(summary?.runningPreviews ?? 0))],
      [t.averageGeneration, averageGeneration, t.availableApis],
      [t.previewStartup, averagePreviewStartup, t.previewStats.replace('{count}', String(summary?.previewsStarted ?? 0)).replace('{failed}', String(summary?.failedPreviews ?? 0))],
      [t.previewRuntime, previewRuntime, t.previewRuntimeHint],
      [t.latestActivity, latest, t.privateWorkspace],
    ]
  }, [loading, projects, summary, t.activePreviews, t.averageGeneration, t.availableApis, t.durationFormat, t.generatedApis, t.latestActivity, t.loading, t.noActivity, t.ownedByYou, t.previewHint, t.previewRuntime, t.previewRuntimeHint, t.previewStartup, t.previewStats, t.privateWorkspace])

  const activityRows = projects.slice(0, 5).map((project) => [
    project.createdAt ? new Date(project.createdAt).toLocaleDateString() : '-',
    project.name,
    project.apiBaseUrl ? t.deployed : t.created,
  ])

  return (
    <Shell
      title={t.title}
      subtitle={t.subtitle}
    >
      <div className="grid cols-2">
        {metrics.map(([label, value, trend]) => (
          <div className="card kpi" key={label}>
            <div className="label">{label}</div>
            <div className="value">{value}</div>
            <div className="trend">{trend}</div>
          </div>
        ))}
      </div>

      <div style={{ height: 16 }} />

      {!loading && projects.length === 0 ? (
        <>
          <div className="card">
            <div className="panelHeader">
              <div>
                <h3 className="panelTitle">{t.emptyTitle}</h3>
                <p className="panelText">{t.emptyText}</p>
              </div>
              <span className="pill">{t.emptyBadge}</span>
            </div>
            <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
              <a className="btn primary" href="/app/generators">{t.launch}</a>
              <a className="btn" href="/app/docs#get-started">{t.emptyGetStarted}</a>
              <a className="btn" href="/app/docs#generate-api">{t.emptyGenerate}</a>
            </div>
          </div>
          <div style={{ height: 16 }} />
        </>
      ) : null}

      <div className="grid cols-2">
        <div className="card">
          <div className="panelHeader">
            <div>
              <h3 className="panelTitle">{t.activityTitle}</h3>
              <p className="panelText">{t.activityText}</p>
            </div>
            <span className="pill">{t.period}</span>
          </div>
          <div className="tableWrap">
            <table className="table">
              <thead>
                <tr><th>{t.tableDate}</th><th>{t.tableEvent}</th><th>{t.tableStatus}</th></tr>
              </thead>
              <tbody>
                {activityRows.length ? activityRows.map(([date, event, status]) => (
                  <tr key={`${date}-${event}`}><td>{date}</td><td>{event}</td><td><span className="badge good">{status}</span></td></tr>
                )) : (
                  <tr><td colSpan={3}>{loading ? t.loading : t.noProjects}</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        <div className="card">
          <div className="panelHeader">
            <div>
              <h3 className="panelTitle">{t.quickTitle}</h3>
              <p className="panelText">{t.quickText}</p>
            </div>
          </div>
          <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))' }}>
            <a className="btn primary" href="/app/generators">{t.launch}</a>
            <a className="btn" href="/app/docs">{t.readDocs}</a>
            <a className="btn" href="/app/docs#preview-api">{t.previewGuide}</a>
            <a className="btn" href="/app/docs#common-errors">{t.fixErrors}</a>
          </div>
          <div className="hr" />
          <p className="panelText">{t.note}</p>
        </div>
      </div>

      <div style={{ height: 16 }} />

      {failedPreviews.length ? (
        <>
          <div className="card">
            <div className="panelHeader">
              <div>
                <h3 className="panelTitle">{t.failedPreviewsTitle}</h3>
                <p className="panelText">{t.failedPreviewsText}</p>
              </div>
              <span className="badge bad">{failedPreviews.length}</span>
            </div>
            <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))' }}>
              {failedPreviews.map((preview) => (
                <div key={preview.generatedApiId} className="docStep">
                  <h4>{preview.generatedApiName}</h4>
                  <p>{preview.errorHint ?? preview.errorMessage ?? t.failedPreviewFallback}</p>
                  <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 10 }}>
                    <a className="btn primary" href="/app/generators">{t.investigatePreview}</a>
                    <a className="btn" href="/app/docs#common-errors">{t.fixErrors}</a>
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div style={{ height: 16 }} />
        </>
      ) : null}

      <div className="card">
        <div className="panelHeader">
          <div>
            <h3 className="panelTitle">{t.roadmapTitle}</h3>
            <p className="panelText">{t.roadmapText}</p>
          </div>
        </div>
        <div className="tableWrap">
          <table className="table">
            <thead><tr><th>{t.roadmapFeature}</th><th>{t.roadmapStatus}</th></tr></thead>
            <tbody>
              {t.roadmapItems.map(([feature, status]) => (
                <tr key={feature}><td>{feature}</td><td><span className="pill">{status}</span></td></tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </Shell>
  )
}
