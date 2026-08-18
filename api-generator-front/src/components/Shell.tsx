import React, { useEffect, useState } from 'react'
import { Sidebar } from './Sidebar'
import { LanguageToggle } from './LanguageToggle'
import { BetaLine } from './BetaLine'
import { useLanguage } from '../i18n/LanguageProvider'

export function Shell({
  title,
  subtitle,
  actions,
  children,
}: {
  readonly title: string
  readonly subtitle?: string
  readonly actions?: React.ReactNode
  readonly children: React.ReactNode
}) {
  const [navOpen, setNavOpen] = useState(false)
  const { text } = useLanguage()
  const [isMobileViewport, setIsMobileViewport] = useState(() => {
    if (typeof window === 'undefined') return false
    return window.matchMedia('(max-width: 979px)').matches
  })
  const [theme, setTheme] = useState<'light' | 'dark'>(() => {
    if (typeof window === 'undefined') return 'light'
    return window.localStorage.getItem('ui-theme') === 'dark' ? 'dark' : 'light'
  })

  useEffect(() => {
    const onPop = () => setNavOpen(false)
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])

  useEffect(() => {
    if (typeof window === 'undefined') return

    const mediaQuery = window.matchMedia('(max-width: 979px)')
    const onChange = (event: MediaQueryListEvent) => setIsMobileViewport(event.matches)

    setIsMobileViewport(mediaQuery.matches)
    mediaQuery.addEventListener('change', onChange)

    return () => mediaQuery.removeEventListener('change', onChange)
  }, [])

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    window.localStorage.setItem('ui-theme', theme)
  }, [theme])

  return (
    <div className="app">
      <Sidebar
        open={navOpen}
        collapsed={false}
        isMobileViewport={isMobileViewport}
        onClose={() => setNavOpen(false)}
      />
      <main className="main">
        <div className="header">
          <div className="hgroup">
            <button className="burger" onClick={() => setNavOpen(true)} aria-label="Open navigation">
              {text.shell.menu}
            </button>
            <div>
              <h1>{title}</h1>
              {subtitle ? <p>{subtitle}</p> : null}
            </div>
          </div>
          <div className="topbar">
            {actions}
            <button
              className="figmaThemeButton"
              type="button"
              aria-label={text.shell.themeToggle}
              title={text.shell.themeToggle}
              onClick={() => setTheme((current) => current === 'dark' ? 'light' : 'dark')}
            >
              {theme === 'dark' ? (
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <circle cx="12" cy="12" r="4" />
                  <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
                </svg>
              ) : (
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M20 14.4A7.7 7.7 0 0 1 9.6 4a8.2 8.2 0 1 0 10.4 10.4Z" />
                </svg>
              )}
            </button>
            <LanguageToggle className="figmaLanguageButton" variant="compact" />
          </div>
        </div>
        <BetaLine />
        <div style={{ height: 16 }} />
        <div className="appContentStack">
          {children}
        </div>
      </main>
    </div>
  )
}
