import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, beforeEach } from 'vitest'
import { LanguageToggle } from '../components/LanguageToggle'
import { LanguageProvider, useLanguage } from './LanguageProvider'

function LocaleProbe() {
  const { locale } = useLanguage()
  return <div data-testid="locale">{locale}</div>
}

function renderLanguageProvider() {
  return render(
    <LanguageProvider>
      <LocaleProbe />
      <LanguageToggle />
    </LanguageProvider>,
  )
}

function mockNavigatorLanguages(languages: readonly string[], language = languages[0] ?? '') {
  Object.defineProperty(window.navigator, 'languages', {
    configurable: true,
    get: () => languages,
  })
  Object.defineProperty(window.navigator, 'language', {
    configurable: true,
    get: () => language,
  })
}

describe('LanguageProvider', () => {
  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.lang = ''
    mockNavigatorLanguages(['en-US'], 'en-US')
  })

  it('uses French when there is no stored locale and the browser language is fr-FR', async () => {
    mockNavigatorLanguages(['fr-FR'], 'fr-FR')

    renderLanguageProvider()

    expect(screen.getByTestId('locale')).toHaveTextContent('fr')
    await waitFor(() => expect(window.localStorage.getItem('ui-language')).toBe('fr'))
    expect(document.documentElement.lang).toBe('fr')
  })

  it('uses French when there is no stored locale and the browser language is fr-BE', async () => {
    mockNavigatorLanguages(['fr-BE'], 'fr-BE')

    renderLanguageProvider()

    expect(screen.getByTestId('locale')).toHaveTextContent('fr')
    await waitFor(() => expect(window.localStorage.getItem('ui-language')).toBe('fr'))
  })

  it('uses English when there is no stored locale and the browser language is en-US', async () => {
    mockNavigatorLanguages(['en-US'], 'en-US')

    renderLanguageProvider()

    expect(screen.getByTestId('locale')).toHaveTextContent('en')
    await waitFor(() => expect(window.localStorage.getItem('ui-language')).toBe('en'))
    expect(document.documentElement.lang).toBe('en')
  })

  it('uses English when there is no stored locale and the browser language is de-DE', async () => {
    mockNavigatorLanguages(['de-DE'], 'de-DE')

    renderLanguageProvider()

    expect(screen.getByTestId('locale')).toHaveTextContent('en')
    await waitFor(() => expect(window.localStorage.getItem('ui-language')).toBe('en'))
  })

  it('prioritizes a stored French locale over an English browser language', () => {
    window.localStorage.setItem('ui-language', 'fr')
    mockNavigatorLanguages(['en-US'], 'en-US')

    renderLanguageProvider()

    expect(screen.getByTestId('locale')).toHaveTextContent('fr')
  })

  it('prioritizes a stored English locale over a French browser language', () => {
    window.localStorage.setItem('ui-language', 'en')
    mockNavigatorLanguages(['fr-FR'], 'fr-FR')

    renderLanguageProvider()

    expect(screen.getByTestId('locale')).toHaveTextContent('en')
  })

  it('changes the language when LanguageToggle is clicked and saves the choice', async () => {
    mockNavigatorLanguages(['en-US'], 'en-US')

    renderLanguageProvider()

    expect(screen.getByTestId('locale')).toHaveTextContent('en')

    fireEvent.click(screen.getByRole('button', { name: /change language/i }))

    expect(screen.getByTestId('locale')).toHaveTextContent('fr')
    await waitFor(() => expect(window.localStorage.getItem('ui-language')).toBe('fr'))
    expect(document.documentElement.lang).toBe('fr')
  })
})
