import { fireEvent, render, screen } from '@testing-library/react'
import Login from './Login'
import { en } from '../i18n/en'
import { vi } from 'vitest'
import { ApiError } from '../services/api'

const navigate = vi.fn()
let currentPath = '/login'
const authState = {
  login: vi.fn(),
  register: vi.fn(),
}

vi.mock('../components/BrandLogo', () => ({
  BrandLogo: ({ className }: { className?: string }) => <div className={className}>Brand</div>,
}))

vi.mock('../components/BetaLine', () => ({
  BetaLine: () => <div>Product in beta. Some features, quotas, and flows can still change.</div>,
}))

vi.mock('../components/LanguageToggle', () => ({
  LanguageToggle: () => <button type="button">Lang</button>,
}))

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigate,
  useLocation: () => ({ pathname: currentPath, state: null }),
}))

vi.mock('../i18n/LanguageProvider', () => ({
  useLanguage: () => ({ text: en, locale: 'en' }),
}))

vi.mock('../state/auth', () => ({
  useAuth: () => authState,
}))

vi.mock('../services/api', async () => {
  const actual = await vi.importActual('../services/api')
  return {
    ...actual,
    api: {
      googleAuthUrl: vi.fn(() => '/oauth/google'),
      oauth2Status: vi.fn().mockResolvedValue({ googleEnabled: false }),
      requestPasswordReset: vi.fn(),
      resetPassword: vi.fn(),
      resendVerificationEmail: vi.fn(),
    },
  }
})

describe('Login', () => {
  beforeEach(() => {
    currentPath = '/login'
    authState.login.mockReset()
    authState.register.mockReset()
  })

  it('renders the beta line and validates missing identifier', async () => {
    render(<Login />)

    expect(screen.getByText('Brand')).toBeInTheDocument()
    expect(screen.getByText('Product in beta. Some features, quotas, and flows can still change.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to home' })).toHaveAttribute('href', '/')

    fireEvent.submit(screen.getByRole('button', { name: 'Sign in' }).closest('form')!)

    expect(await screen.findByText('Enter your email or identifier.')).toBeInTheDocument()
  })

  it('shows a clearer message when verification email cannot be sent after registration', async () => {
    currentPath = '/register'
    authState.register.mockRejectedValue(new ApiError(503, 'Account created, but verification email could not be sent. Try again later.'))

    render(<Login />)

    fireEvent.change(screen.getByLabelText('Email / username'), { target: { value: 'beta@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'strong-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('Account created, but the verification email could not be sent right now. Try again later.')).toBeInTheDocument()
  })

  it('shows a clearer message when the reset code is invalid', async () => {
    currentPath = '/reset-password'
    const { api } = await import('../services/api')
    vi.mocked(api.resetPassword).mockRejectedValue(new ApiError(400, 'Invalid or expired reset code'))

    render(<Login />)

    fireEvent.change(screen.getByLabelText('Email / username'), { target: { value: 'reset-code' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'strong-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByText('The reset code is invalid or expired.')).toBeInTheDocument()
  })
})
