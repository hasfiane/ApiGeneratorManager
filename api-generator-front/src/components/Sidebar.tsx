import React from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { Icon } from './Icon'
import { BrandLogo } from './BrandLogo'
import { useAuth } from '../state/auth'
import { useLanguage } from '../i18n/LanguageProvider'
import { docsContent } from '../content/docsContent'

type IconName = React.ComponentProps<typeof Icon>['name']

interface ItemProps {
  readonly to: string
  readonly label: string
  readonly icon: IconName
  readonly collapsed?: boolean
  readonly onClick?: () => void
  readonly end?: boolean
}

function Item({ to, label, icon, collapsed = false, onClick, end }: ItemProps) {
  return (
    <NavLink
      to={to}
      end={end}
      onClick={onClick}
      className={({ isActive }) => isActive ? 'active' : ''}
      title={collapsed ? label : undefined}
      aria-label={label}
    >
      <span className={collapsed ? 'navItemContent collapsed' : 'navItemContent'}>
        <Icon name={icon} />
        <span className={collapsed ? 'navItemLabel hidden' : 'navItemLabel'}>{label}</span>
      </span>
    </NavLink>
  )
}

interface SidebarProps {
  readonly open: boolean
  readonly collapsed: boolean
  readonly isMobileViewport?: boolean
  readonly onToggleCollapsed?: () => void
  readonly onClose: () => void
}

export function Sidebar({
  open,
  collapsed,
  onClose,
}: SidebarProps) {
  const { logout, isAdmin } = useAuth()
  const { text, locale } = useLanguage()
  const navigate = useNavigate()
  const location = useLocation()
  const docSections = docsContent[locale].sections
  const isDocsRoute = location.pathname === '/app/docs'
  const isAdminRoute = location.pathname.startsWith('/app/admin')

  return (
    <>
      <div
        className={open ? 'overlay show' : 'overlay'}
        role="button"
        tabIndex={open ? 0 : -1}
        aria-label={text.shell.closeNavigation}
        onClick={onClose}
        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && onClose()}
      />
      <aside className={`${open ? 'sidebar open' : 'sidebar'}${collapsed ? ' collapsed' : ''}`}>
        <div className="brand">
          <BrandLogo className="brandLogo brandLogo--app" />
          <div className={collapsed ? 'brandCopy hidden' : 'brandCopy'}>
            <div className="brandTitle">Api Generator</div>
            <div className="brandSubtitle">Control room</div>
          </div>
          <button className="closeNav" onClick={onClose} aria-label={text.shell.closeNavigation}>X</button>
        </div>
        <div className="nav">
          <Item to="/app" end label={text.nav.overview} icon="home" collapsed={collapsed} onClick={onClose} />
          <Item to="/app/generators" label={text.nav.generations} icon="bolt" collapsed={collapsed} onClick={onClose} />
          <Item to="/app/security" label={text.nav.security} icon="shield" collapsed={collapsed} onClick={onClose} />
          <Item to="/app/docs" label={text.nav.docs} icon="doc" collapsed={collapsed} onClick={onClose} />
          {isAdmin && <Item to="/app/admin" label={text.nav.admin} icon="shield" collapsed={collapsed} onClick={onClose} />}
          {isAdmin && isAdminRoute && !collapsed && (
            <div className="navSubmenu" aria-label={text.nav.admin}>
              <NavLink to="/app/admin" end onClick={onClose}>{text.nav.adminDashboard}</NavLink>
              <NavLink to="/app/admin/database" onClick={onClose}>{text.nav.adminDatabase}</NavLink>
            </div>
          )}
          {isDocsRoute && !collapsed && (
            <div className="navSubmenu" aria-label={text.nav.docs}>
              {docSections.map((section) => (
                <a key={section.id} href={`/app/docs#${section.id}`} onClick={onClose}>
                  {section.title}
                </a>
              ))}
            </div>
          )}
        </div>

        <div className="sidebarFooter">
          <button
            className="btn"
            style={{ width: '100%' }}
            aria-label={text.nav.logout}
            title={collapsed ? text.nav.logout : undefined}
            onClick={() => {
              void logout().finally(() => {
                onClose()
                navigate('/login', { replace: true })
              })
            }}
          >
            <Icon name="logout" />
            <span className={collapsed ? 'navItemLabel hidden' : 'navItemLabel'}>{text.nav.logout}</span>
          </button>
        </div>
      </aside>
    </>
  )
}
