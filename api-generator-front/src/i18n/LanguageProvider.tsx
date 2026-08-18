/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { en } from './en'
import { fr } from './fr'

type Locale = 'fr' | 'en'
type Messages = typeof fr

const STORAGE_KEY = 'ui-language'
const messages: Record<Locale, Messages> = { fr, en }

function isLocale(value: string | null): value is Locale {
  return value === 'fr' || value === 'en'
}

function detectBrowserLocale(): Locale {
  if (typeof window === 'undefined') return 'en'

  const stored = window.localStorage.getItem(STORAGE_KEY)
  if (isLocale(stored)) return stored

  const languages = window.navigator.languages?.length
    ? window.navigator.languages
    : [window.navigator.language]

  const normalized = languages
    .filter(Boolean)
    .map((language) => language.toLowerCase())

  if (normalized.some((language) => language.startsWith('fr'))) {
    return 'fr'
  }

  return 'en'
}

type LanguageContext = {
  locale: Locale
  text: Messages
  toggleLanguage: () => void
}

const Ctx = createContext<LanguageContext | null>(null)

export function LanguageProvider({ children }: { readonly children: ReactNode }) {
  const [locale, setLocale] = useState<Locale>(detectBrowserLocale)

  useEffect(() => {
    document.documentElement.lang = locale
    window.localStorage.setItem(STORAGE_KEY, locale)
  }, [locale])

  const value = useMemo(() => ({
    locale,
    text: messages[locale],
    toggleLanguage: () => setLocale((current) => current === 'fr' ? 'en' : 'fr'),
  }), [locale])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useLanguage() {
  const context = useContext(Ctx)
  if (!context) throw new Error('useLanguage must be used inside LanguageProvider')
  return context
}
