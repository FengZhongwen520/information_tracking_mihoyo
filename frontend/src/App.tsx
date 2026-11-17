import { useEffect, useRef, useState, type ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { api, getToken, setToken } from './api'
import { AppHeader } from './components/AppHeader'
import { SmoothScroll, useSmoothScroll } from './components/SmoothScroll'
import {
  applyTheme,
  readThemeMode,
  resolveTheme,
  storeThemeMode,
  type ResolvedTheme,
  type ThemeMode,
} from './lib/theme'
import { Banners } from './pages/Banners'
import { Challenges } from './pages/Challenges'
import { Codes } from './pages/Codes'
import { Dashboard } from './pages/Dashboard'
import { Login } from './pages/Login'
import { Settings } from './pages/Settings'
import type { User } from './types'

const HAS_NATIVE_TRANSITION =
  typeof document !== 'undefined' && 'startViewTransition' in document

function ScrollToTop() {
  const { pathname } = useLocation()
  const lenis = useSmoothScroll()
  useEffect(() => {
    if (lenis) {
      lenis.scrollTo(0, { immediate: true, force: true })
    } else {
      window.scrollTo(0, 0)
    }
  }, [pathname, lenis])
  return null
}

function RouteStage({ children }: { children: ReactNode }) {
  const { pathname } = useLocation()
  const firstRender = useRef(true)

  useEffect(() => {
    firstRender.current = false
  }, [])

  const fallback = !HAS_NATIVE_TRANSITION && !firstRender.current

  return (
    <div className={`route-stage${fallback ? ' route-stage-fallback' : ''}`} key={pathname}>
      {children}
    </div>
  )
}

export default function App() {
  const [user, setUser] = useState<User | null>(null)
  const [booting, setBooting] = useState(true)
  const [themeMode, setThemeMode] = useState<ThemeMode>(readThemeMode)
  const [theme, setTheme] = useState<ResolvedTheme>(() => resolveTheme(readThemeMode()))

  useEffect(() => {
    const next = resolveTheme(themeMode)
    setTheme(next)
    applyTheme(next)
    if (themeMode !== 'system') return

    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => {
      const resolved = resolveTheme('system', media.matches)
      setTheme(resolved)
      applyTheme(resolved, true)
    }
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [themeMode])

  function changeThemeMode(next: ThemeMode) {
    storeThemeMode(next)
    const resolved = resolveTheme(next)
    setTheme(resolved)
    setThemeMode(next)
    applyTheme(resolved, true)
  }

  useEffect(() => {
    if (!getToken()) {
      setBooting(false)
      return
    }
    let alive = true
    api
      .me()
      .then((current) => {
        if (alive) setUser(current)
      })
      .catch(() => setToken(null))
      .finally(() => {
        if (alive) setBooting(false)
      })
    return () => {
      alive = false
    }
  }, [])

  useEffect(() => {
    const expire = () => setUser(null)
    window.addEventListener('auth:expired', expire)
    return () => window.removeEventListener('auth:expired', expire)
  }, [])

  function logout() {
    setToken(null)
    setUser(null)
  }

  if (booting) {
    return (
      <div className="boot-screen">
        <span className="boot-mark">追</span>
        <span>正在载入…</span>
      </div>
    )
  }

  return (
    <SmoothScroll>
      <BrowserRouter>
        <ScrollToTop />
        <div className="app-shell">
          <AppHeader
            user={user}
            onLogout={logout}
            theme={theme}
            onToggleTheme={() => changeThemeMode(theme === 'dark' ? 'light' : 'dark')}
          />
          <RouteStage>
            <Routes>
              <Route
                path="/login"
                element={user ? <Navigate to="/" replace /> : <Login onAuthed={setUser} />}
              />
              <Route path="/" element={<Dashboard user={user} />} />
              <Route path="/challenges" element={<Challenges user={user} />} />
              <Route path="/codes" element={<Codes user={user} />} />
              <Route path="/banners" element={<Banners user={user} />} />
              <Route
                path="/settings"
                element={
                  <Settings
                    user={user}
                    onLogout={logout}
                    themeMode={themeMode}
                    onThemeChange={changeThemeMode}
                  />
                }
              />
              <Route path="/extras" element={<Navigate to="/codes" replace />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </RouteStage>
          <footer className="app-footer">
            <span>无需登录即可浏览和设置订阅。登录后可以标记完成，记录按账号保存。</span>
          </footer>
        </div>
      </BrowserRouter>
    </SmoothScroll>
  )
}
