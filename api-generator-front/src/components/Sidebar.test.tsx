import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import { Sidebar } from './Sidebar'
import { en } from '../i18n/en'

const logout = vi.fn().mockResolvedValue(undefined)
let isAdmin = false

vi.mock('./BrandLogo', () => ({
  BrandLogo: ({ className }: { className?: string }) => <div className={className}>Brand</div>,
}))

vi.mock('./Icon', () => ({
  Icon: ({ name }: { name: string }) => <span>{name}</span>,
}))

vi.mock('../state/auth', () => ({
  useAuth: () => ({
    logout,
    isAdmin,
  }),
}))

vi.mock('../i18n/LanguageProvider', () => ({
  useLanguage: () => ({ text: en, locale: 'en' }),
}))

describe('Sidebar', () => {
  beforeEach(() => {
    isAdmin = false
    logout.mockClear()
  })

  it('shows docs anchors on the docs route and keeps logout available', async () => {
    render(
      <MemoryRouter initialEntries={['/app/docs']}>
        <Sidebar open collapsed={false} onToggleCollapsed={vi.fn()} onClose={vi.fn()} />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Get started' })).toHaveAttribute('href', '/app/docs#get-started')
    expect(screen.getByRole('link', { name: 'Preview and test' })).toHaveAttribute('href', '/app/docs#preview-api')

    fireEvent.click(screen.getByRole('button', { name: /sign out/i }))

    expect(logout).toHaveBeenCalledTimes(1)
  })

  it('hides admin navigation for non-admin users', () => {
    render(
      <MemoryRouter initialEntries={['/app']}>
        <Sidebar open collapsed={false} onToggleCollapsed={vi.fn()} onClose={vi.fn()} />
      </MemoryRouter>,
    )

    expect(screen.queryByRole('link', { name: en.nav.admin })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: en.nav.security })).toHaveAttribute('href', '/app/security')
  })

  it('shows admin navigation and submenu for admin users', () => {
    isAdmin = true

    render(
      <MemoryRouter initialEntries={['/app/admin']}>
        <Sidebar open collapsed={false} onToggleCollapsed={vi.fn()} onClose={vi.fn()} />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: en.nav.admin })).toHaveAttribute('href', '/app/admin')
    expect(screen.getByRole('link', { name: en.nav.adminDashboard })).toHaveAttribute('href', '/app/admin')
    expect(screen.getByRole('link', { name: en.nav.adminDatabase })).toHaveAttribute('href', '/app/admin/database')
  })

  it('hides the collapse action on mobile viewport', () => {
    render(
      <MemoryRouter initialEntries={['/app']}>
        <Sidebar
          open
          collapsed={false}
          isMobileViewport
          onToggleCollapsed={vi.fn()}
          onClose={vi.fn()}
        />
      </MemoryRouter>,
    )

    expect(screen.queryByRole('button', { name: /collapse navigation/i })).not.toBeInTheDocument()
  })
})
