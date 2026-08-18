import { useEffect, useState } from 'react'
import { Shell } from '../components/Shell'
import { api, type ApiProject } from '../services/api'

export default function Databases() {
  const [projects, setProjects] = useState<ApiProject[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    void api.getMyApis()
      .then((items) => {
        if (active) setProjects(items)
      })
      .catch(() => {
        if (active) setProjects([])
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [])

  return (
    <Shell
      title="Profils BDD"
      subtitle="Connexions utilisees par tes generations."
      actions={<a className="btn primary" href="/app/generators">Nouvelle generation</a>}
    >
      <div className="card">
        <div className="panelHeader">
          <div>
            <h3 className="panelTitle">Connexions</h3>
            <p className="panelText">Historique limite a ton compte.</p>
          </div>
          <span className="pill">{projects.length} profils</span>
        </div>
        <div className="tableWrap">
          <table className="table">
            <thead><tr><th>Nom</th><th>Type</th><th>Job</th><th>Statut</th><th>Action</th></tr></thead>
            <tbody>
              {projects.length ? projects.map((project) => (
                <tr key={project.id}>
                  <td>{project.name}</td>
                  <td>{project.dbType || '-'}</td>
                  <td style={{ fontFamily: 'var(--mono)' }}>{project.jobId || '-'}</td>
                  <td><span className={project.apiBaseUrl ? 'badge good' : 'badge warn'}>{project.apiBaseUrl ? 'Deploye' : 'Cree'}</span></td>
                  <td>{project.apiBaseUrl ? <a className="btn" href={project.apiBaseUrl} target="_blank" rel="noreferrer">Ouvrir</a> : '-'}</td>
                </tr>
              )) : (
                <tr><td colSpan={5}>{loading ? 'Chargement...' : 'Aucune connexion pour ce compte.'}</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </Shell>
  )
}
