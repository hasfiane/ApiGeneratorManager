import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Security from './Security'
import { en } from '../i18n/en'

const { getSecurityDeployments } = vi.hoisted(() => ({ getSecurityDeployments: vi.fn() }))

vi.mock('../components/Shell', () => ({ Shell: ({ children }: { children: ReactNode }) => <main>{children}</main> }))
vi.mock('../i18n/LanguageProvider', () => ({ useLanguage: () => ({ text: en }) }))
vi.mock('../services/api', () => ({ api: { getSecurityDeployments }, }))

describe('Security page', () => {
  beforeEach(() => getSecurityDeployments.mockReset())

  it('shows the empty state without security demo data', async () => {
    getSecurityDeployments.mockResolvedValue([])
    render(<MemoryRouter><Security /></MemoryRouter>)
    expect(await screen.findByText('No deployed API')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to generations' })).toHaveAttribute('href', '/app/generators')
    expect(screen.queryByText('Users')).not.toBeInTheDocument()
  })

  it('selects a single deployed API automatically', async () => {
    getSecurityDeployments.mockResolvedValue([{ id: 'one', name: 'Orders API', status: 'RUNNING' }])
    render(<MemoryRouter><Security /></MemoryRouter>)
    expect(await screen.findByText('Orders API')).toBeInTheDocument()
    expect(screen.getByText('JWT keys')).toBeInTheDocument()
  })

  it('requires selection when several APIs are deployed', async () => {
    getSecurityDeployments.mockResolvedValue([
      { id: 'one', name: 'Orders API', status: 'RUNNING' },
      { id: 'two', name: 'Billing API', status: 'RUNNING' },
    ])
    render(<MemoryRouter><Security /></MemoryRouter>)
    expect(await screen.findByText('Choose a deployed API')).toBeInTheDocument()
    expect(screen.getByText('Orders API')).toBeInTheDocument()
    expect(screen.queryByText('JWT keys')).not.toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('Billing API')).toBeInTheDocument())
  })
})
