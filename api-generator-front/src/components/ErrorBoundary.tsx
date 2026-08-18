import React from 'react'

interface Props {
  children: React.ReactNode
  fallback?: React.ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
}

export class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, info: React.ErrorInfo): void {
    console.error('[ErrorBoundary]', error, info.componentStack)
  }

  handleReset = (): void => {
    this.setState({ hasError: false, error: null })
  }

  render(): React.ReactNode {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback

      return (
        <div className="errorBoundary">
          <div className="errorBoundaryCard">
            <div className="errorBoundaryIcon">!</div>
            <h2>Une erreur est survenue</h2>
            <p className="muted">
              Une erreur inattendue a interrompu l'application. Recharge la page ou reviens a l'accueil.
            </p>
            <button className="btn" onClick={this.handleReset}>
              Réessayer
            </button>
            <button
              className="btn"
              style={{ marginLeft: 8 }}
              onClick={() => window.location.assign('/')}
            >
              Retour à l'accueil
            </button>
          </div>
        </div>
      )
    }

    return this.props.children
  }
}

