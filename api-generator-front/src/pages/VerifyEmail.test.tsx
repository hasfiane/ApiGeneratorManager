import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { vi } from 'vitest'
import VerifyEmail from './VerifyEmail'
import { en } from '../i18n/en'
import { ApiError } from '../services/api'

vi.mock('../components/BetaLine', () => ({
  BetaLine: () => <div>Product in beta. Some features, quotas, and flows can still change.</div>,
}))

vi.mock('../i18n/LanguageProvider', () => ({
  useLanguage: () => ({ text: en, locale: 'en' }),
}))

vi.mock('../services/api', async () => {
  const actual = await vi.importActual('../services/api')
  return {
    ...actual,
    api: {
      verifyEmail: vi.fn(),
    },
  }
})

describe('VerifyEmail', () => {
  it('verifies the token from the URL and keeps the beta line visible', async () => {
    const { api } = await import('../services/api')
    vi.mocked(api.verifyEmail).mockResolvedValue(undefined as never)

    render(
      <MemoryRouter initialEntries={['/verify-email?token=abc-123']}>
        <Routes>
          <Route path="/verify-email" element={<VerifyEmail />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('Product in beta. Some features, quotas, and flows can still change.')).toBeInTheDocument()

    await waitFor(() => {
      expect(api.verifyEmail).toHaveBeenCalledWith('abc-123')
    })

    expect(await screen.findByText('Email verified. You can sign in.')).toBeInTheDocument()
  })

  it('shows a clearer message when verification is rate limited', async () => {
    const { api } = await import('../services/api')
    vi.mocked(api.verifyEmail).mockRejectedValue(new ApiError(429, 'Too many attempts. Please try again later.'))

    render(
      <MemoryRouter initialEntries={['/verify-email?token=abc-123']}>
        <Routes>
          <Route path="/verify-email" element={<VerifyEmail />} />
        </Routes>
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(api.verifyEmail).toHaveBeenCalledWith('abc-123')
    })

    expect(await screen.findByText('Too many verification attempts. Try again later.')).toBeInTheDocument()
  })
})
