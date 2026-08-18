import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './routes/App'
import { ErrorBoundary } from './components/ErrorBoundary'
import './styles.css'

const rootEl = document.getElementById('root')
if (!rootEl) throw new Error('Element #root introuvable dans index.html')

ReactDOM.createRoot(rootEl).render(
  <React.StrictMode>
    <ErrorBoundary>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </ErrorBoundary>
  </React.StrictMode>
)
