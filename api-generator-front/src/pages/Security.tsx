import { useEffect, useState } from 'react'
import { Shell } from '../components/Shell'
import { useLanguage } from '../i18n/LanguageProvider'
import { api, type SecurityDeployment } from '../services/api'

const sections = ['overview', 'users', 'roles', 'serviceAccounts', 'sessions', 'jwtKeys', 'audit'] as const

export default function Security() {
  const { text } = useLanguage()
  const t = text.security
  const [deployments, setDeployments] = useState<SecurityDeployment[]>([])
  const [selected, setSelected] = useState<SecurityDeployment | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    void api.getSecurityDeployments()
      .then((items) => {
        if (!active) return
        setDeployments(items)
        setSelected(items.length === 1 ? items[0] : null)
      })
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [])

  if (loading) {
    return <Shell title={t.title} subtitle={t.subtitle}><div className="card">{t.loading}</div></Shell>
  }

  if (deployments.length === 0) {
    return (
      <Shell title={t.title} subtitle={t.subtitle}>
        <div className="card">
          <h2 className="panelTitle">{t.emptyTitle}</h2>
          <p className="panelText">{t.emptyText}</p>
          <a className="btn primary" href="/app/generators">{t.generate}</a>
        </div>
      </Shell>
    )
  }

  if (!selected) {
    return (
      <Shell title={t.title} subtitle={t.subtitle}>
        <div className="card">
          <h2 className="panelTitle">{t.selectTitle}</h2>
          <p className="panelText">{t.selectText}</p>
        </div>
        <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))' }}>
          {deployments.map((deployment) => (
            <button className="card" type="button" key={deployment.id} onClick={() => setSelected(deployment)}>
              <h3 className="panelTitle">{deployment.name}</h3>
              <span className="badge good">{deployment.status}</span>
            </button>
          ))}
        </div>
      </Shell>
    )
  }

  return (
    <Shell title={t.title} subtitle={t.subtitle}>
      {deployments.length > 1 ? (
        <button className="btn" type="button" onClick={() => setSelected(null)}>{t.selectTitle}</button>
      ) : null}
      <div className="card">
        <div className="panelHeader">
          <h2 className="panelTitle">{selected.name}</h2>
          <span className="badge good">{selected.status}</span>
        </div>
        <p className="panelText">{t.managementUnavailable}</p>
      </div>
      <div className="grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' }}>
        {sections.map((section) => <div className="card" key={section}><h3 className="panelTitle">{t[section]}</h3></div>)}
      </div>
    </Shell>
  )
}
