import { fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { vi } from 'vitest'
import Docs from './Docs'
import { en } from '../i18n/en'

vi.mock('../components/Shell', () => ({
  Shell: ({ title, subtitle, actions, children }: { title: string; subtitle?: string; actions?: ReactNode; children: ReactNode }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {actions}
      {children}
    </div>
  ),
}))

vi.mock('../i18n/LanguageProvider', () => ({
  useLanguage: () => ({ text: en, locale: 'en' }),
}))

describe('Docs', () => {
  it('renders the summary navigation and jumps to a selected section', () => {
    render(<Docs />)

    expect(screen.getByText('Help center')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'New generation' })).toHaveAttribute('href', '/app/generators')
    expect(screen.getByRole('link', { name: /test and fix/i })).toHaveAttribute('href', '#preview-api')

    fireEvent.change(screen.getByLabelText('Go to a section'), {
      target: { value: 'common-errors' },
    })

    expect(window.location.hash).toBe('#common-errors')
    expect(screen.getByText('Host diagnostics failing')).toBeInTheDocument()
    expect(screen.getByText('Recent preview failure')).toBeInTheDocument()
    expect(screen.getAllByText('v')).not.toHaveLength(0)
  })
})
