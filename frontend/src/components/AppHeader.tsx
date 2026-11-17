import { Link, NavLink, useLocation } from 'react-router-dom'
import type { User } from '../types'

interface Props {
  user: User | null
  onLogout: () => void
  theme: 'light' | 'dark'
  onToggleTheme: () => void
}

function SunIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="17"
      height="17"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="4" /> {/* deslop-ignore 24 */}
      <path d="M12 2.5v2M12 19.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4" />
    </svg>
  )
}

function MoonIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="17"
      height="17"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z" />
    </svg>
  )
}

export function AppHeader({ user, onLogout, theme, onToggleTheme }: Props) {
  const location = useLocation()

  return (
    <header className="app-header">
      <Link to="/" className="brand" aria-label="返回活动看板" viewTransition>
        <img className="brand-mark" src="/app-icon-512.png" alt="" />
        <span className="brand-text">
          <strong>今天也在米哈打工</strong>
        </span>
      </Link>

      <nav className="app-nav" aria-label="主导航" data-lenis-prevent-touch>
        <NavLink to="/" end viewTransition className={({ isActive }) => (isActive ? 'is-active' : '')}>
          活动看板
        </NavLink>
        <NavLink to="/challenges" viewTransition className={({ isActive }) => (isActive ? 'is-active' : '')}>
          挑战看板
        </NavLink>
        <NavLink to="/codes" viewTransition className={({ isActive }) => (isActive ? 'is-active' : '')}>
          兑换码
        </NavLink>
        <NavLink to="/banners" viewTransition className={({ isActive }) => (isActive ? 'is-active' : '')}>
          卡池
        </NavLink>
        <NavLink to="/settings" viewTransition className={({ isActive }) => (isActive ? 'is-active' : '')}>
          设置
        </NavLink>
      </nav>

      <div className="system-status">
        <span className="system-item">
          <span className="system-dot" />
          HoYo
        </span>
        <button
          type="button"
          className="theme-toggle"
          onClick={onToggleTheme}
          aria-label={theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'}
          title={theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'}
        >
          {theme === 'dark' ? <SunIcon /> : <MoonIcon />}
        </button>
        {user ? (
          <div className="user-box">
            <span className="user-name" title={user.id}>
              {user.username}
            </span>
            <button type="button" className="text-button" onClick={onLogout}>
              退出
            </button>
          </div>
        ) : (
          <Link
            to="/login"
            state={{ from: `${location.pathname}${location.search}` }}
            className="text-link"
            viewTransition
          >
            登录
          </Link>
        )}
      </div>
    </header>
  )
}
