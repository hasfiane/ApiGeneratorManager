import { describe, expect, it } from 'vitest'
import appSource from './App.tsx?raw'

function routeLine(path: string): string {
  return appSource.split('\n').find((line) => line.includes(`path="${path}"`)) ?? ''
}

describe('App route guards', () => {
  it('redirects the root and unknown routes to login', () => {
    expect(routeLine('/')).toContain('to="/login"')
    expect(routeLine('*')).toContain('to="/login"')
  })

  it('keeps application routes protected', () => {
    expect(routeLine('/app')).toContain('<ProtectedPage>')
    expect(routeLine('/app/generators')).toContain('<ProtectedPage>')
    expect(routeLine('/app/security')).toContain('<ProtectedPage>')
    expect(routeLine('/app/docs')).toContain('<ProtectedPage>')
  })

  it('keeps administration routes behind AdminPage', () => {
    expect(routeLine('/app/admin')).toContain('<AdminPage>')
    expect(routeLine('/app/admin/database')).toContain('<AdminPage>')
    expect(appSource).toContain('function AdminOnly')
  })

  it('contains no public marketing or SEO routes', () => {
    expect(appSource).not.toContain('SeoLanding')
    expect(appSource).not.toContain('SeoArticlePage')
    expect(appSource).not.toContain('ComparisonPage')
    expect(appSource).not.toContain('PublicDocsPage')
  })
})
