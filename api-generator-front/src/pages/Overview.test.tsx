import type { ReactNode } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import Overview from './Overview'
import { en } from '../i18n/en'
import { vi } from 'vitest'

vi.mock('../components/Shell', () => ({
  Shell: ({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}))

vi.mock('../i18n/LanguageProvider', () => ({
  useLanguage: () => ({ text: en, locale: 'en' }),
}))

vi.mock('../services/api', () => ({
  api: {
    getMyApis: vi.fn(),
    getAccountSummary: vi.fn(),
    getRecentFailedPreviews: vi.fn(),
  },
}))

describe('Overview', () => {
  it('shows recent failed previews from the dedicated endpoint', async () => {
    const { api } = await import('../services/api')
    vi.mocked(api.getMyApis).mockResolvedValue([
      { id: 'api-1', name: 'Orders API', createdAt: '2026-04-23T10:00:00Z' },
    ] as never)
    vi.mocked(api.getAccountSummary).mockResolvedValue({
      totalGeneratedApis: 1,
      completedGenerations: 1,
      failedGenerations: 0,
      averageGenerationSeconds: 20,
      activePreviews: 0,
      runningPreviews: 0,
      previewsStarted: 1,
      failedPreviews: 1,
      averagePreviewStartupSeconds: 12,
      averagePreviewRuntimeSeconds: 0,
    } as never)
    vi.mocked(api.getRecentFailedPreviews).mockResolvedValue([
      {
        generatedApiId: 'api-1',
        generatedApiName: 'Orders API',
        previewStatus: 'FAILED',
        errorCode: 'PREVIEW_BUILD_FAILED',
        errorHint: 'Inspect preview logs and Maven artifacts.',
        stoppedAt: '2026-04-23T11:00:00Z',
      },
    ] as never)

    render(<Overview />)

    await waitFor(() => {
      expect(screen.getByText('Failed previews')).toBeInTheDocument()
    })

    expect(screen.getAllByText('Orders API')).toHaveLength(2)
    expect(screen.getByText('Inspect preview logs and Maven artifacts.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Investigate' })).toBeInTheDocument()
  })
})
