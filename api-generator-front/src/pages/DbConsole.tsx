import { Shell } from '../components/Shell'
import { env } from '../services/env'

export default function DbConsole() {
  return (
    <Shell title="DB Console" subtitle="Ouvre CloudBeaver pour explorer les bases utilisees par tes generations.">
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <div className="panelHeader" style={{ padding: 16, marginBottom: 0, borderBottom: '1px solid var(--line)' }}>
          <div>
            <h3 className="panelTitle">CloudBeaver</h3>
            <p className="panelText">{env.cloudbeaverUrl}</p>
          </div>
          <a className="btn primary" href={env.cloudbeaverUrl} target="_blank" rel="noreferrer">
            Ouvrir
          </a>
        </div>

        <iframe
          title="CloudBeaver"
          src={env.cloudbeaverUrl}
          style={{ width: '100%', height: 'calc(100vh - 220px)', minHeight: 420, border: 0 }}
          allow="clipboard-read; clipboard-write"
        />
      </div>
    </Shell>
  )
}
