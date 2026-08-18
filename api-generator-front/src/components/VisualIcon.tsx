type VisualIconName =
  | 'api'
  | 'database'
  | 'docs'
  | 'download'
  | 'examples'
  | 'faq'
  | 'history'
  | 'json'
  | 'logs'
  | 'shield'
  | 'success'
  | 'swagger'
  | 'warning'
  | 'yaml'
  | 'zip'

const paths: Record<VisualIconName, readonly string[]> = {
  api: ['M6 7h12v10H6z', 'M9 4v3M15 4v3M9 17v3M15 17v3', 'M4 10h2M4 14h2M18 10h2M18 14h2'],
  database: ['M5 7c0-1.7 3.1-3 7-3s7 1.3 7 3-3.1 3-7 3-7-1.3-7-3z', 'M5 7v5c0 1.7 3.1 3 7 3s7-1.3 7-3V7', 'M5 12v5c0 1.7 3.1 3 7 3s7-1.3 7-3v-5'],
  docs: ['M7 4h7l3 3v13H7z', 'M14 4v4h4', 'M9 12h6M9 15h6M9 18h4'],
  download: ['M12 4v10', 'M8 10l4 4 4-4', 'M5 20h14'],
  examples: ['M5 5h6v6H5z', 'M13 5h6v6h-6z', 'M5 13h6v6H5z', 'M13 13h6v6h-6z'],
  faq: ['M12 18h.01', 'M9.5 9a2.6 2.6 0 0 1 5 1c0 2.2-2.5 2.3-2.5 4.2', 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z'],
  history: ['M5 12a7 7 0 1 0 2-5', 'M5 5v5h5', 'M12 8v5l3 2'],
  json: ['M8 4H6a2 2 0 0 0-2 2v3a2 2 0 0 1-2 2 2 2 0 0 1 2 2v3a2 2 0 0 0 2 2h2', 'M16 4h2a2 2 0 0 1 2 2v3a2 2 0 0 0 2 2 2 2 0 0 0-2 2v3a2 2 0 0 1-2 2h-2'],
  logs: ['M5 6h14', 'M5 11h10', 'M5 16h12'],
  shield: ['M12 3l7 3v5c0 4.4-2.8 8.2-7 10-4.2-1.8-7-5.6-7-10V6z'],
  success: ['M20 6 9 17l-5-5'],
  swagger: ['M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16z', 'M8 12h8', 'M12 8v8'],
  warning: ['M12 4 3 20h18z', 'M12 9v5', 'M12 17h.01'],
  yaml: ['M7 4h10l2 3v13H7z', 'M10 10l2 3 2-3', 'M10 16h4'],
  zip: ['M8 4h7l3 3v13H8z', 'M15 4v4h4', 'M10 7h2M12 9h2M10 11h2M12 13h2', 'M10 17h4'],
}

export function VisualIcon({ name, className = 'visualIcon' }: { readonly name: VisualIconName; readonly className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      {paths[name].map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  )
}
