interface Props {
  message?: string
}

export function LoadingSpinner({ message = 'Chargement…' }: Props) {
  return (
    <div className="loadingSpinner" aria-live="polite" aria-label={message}>
      <div className="spinner" />
      <p className="muted">{message}</p>
    </div>
  )
}

