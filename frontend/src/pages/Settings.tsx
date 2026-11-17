import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, setToken } from '../api'
import { encryptPassword } from '../lib/crypto'
import { formatRelative, GAME_SHORT } from '../lib/format'
import { useReveal } from '../lib/reveal'
import { readLocalGames, writeLocalGames } from '../lib/subscriptions'
import type { ThemeMode } from '../lib/theme'
import type { Game, User } from '../types'

interface Props {
  user: User | null
  onLogout: () => void
  themeMode: ThemeMode
  onThemeChange: (mode: ThemeMode) => void
}

const THEME_OPTIONS: { key: ThemeMode; label: string; hint: string }[] = [
  { key: 'system', label: '跟随系统', hint: '随手机或电脑的深浅色设置自动切换' },
  { key: 'light', label: '浅色', hint: '始终使用浅色界面' },
  { key: 'dark', label: '深色', hint: '始终使用深色界面' },
]

export function Settings({ user, onLogout, themeMode, onThemeChange }: Props) {
  const [games, setGames] = useState<Game[]>([])
  const [selected, setSelected] = useState<string[]>([])
  const [saved, setSaved] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [message, setMessage] = useState<{ type: 'ok' | 'error'; text: string } | null>(null)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordBusy, setPasswordBusy] = useState(false)
  const [passwordMessage, setPasswordMessage] = useState<{ type: 'ok' | 'error'; text: string } | null>(
    null,
  )
  const [recoveryPassword, setRecoveryPassword] = useState('')
  const [recoveryBusy, setRecoveryBusy] = useState(false)
  const [recoveryMessage, setRecoveryMessage] = useState<{ type: 'ok' | 'error'; text: string } | null>(
    null,
  )
  const [issuedRecoveryCode, setIssuedRecoveryCode] = useState<string | null>(null)
  const [recoveryCopied, setRecoveryCopied] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deletePassword, setDeletePassword] = useState('')
  const [deleteBusy, setDeleteBusy] = useState(false)
  const [deleteMessage, setDeleteMessage] = useState<string | null>(null)

  useEffect(() => {
    api
      .games()
      .then((list) => {
        const keys = user
          ? list.filter((game) => game.selected).map((game) => game.key)
          : readLocalGames() ?? list.map((game) => game.key)
        setGames(list)
        setSelected(keys)
        setSaved(keys)
      })
      .catch((ex) => setMessage({ type: 'error', text: ex.message }))
      .finally(() => setLoading(false))
  }, [user?.id])

  const dirty = useMemo(() => {
    return [...saved].sort().join(',') !== [...selected].sort().join(',')
  }, [saved, selected])

  function toggleGame(key: string) {
    setSelected((current) =>
      current.includes(key) ? current.filter((item) => item !== key) : [...current, key],
    )
  }

  async function saveGames() {
    setSaving(true)
    setMessage(null)
    try {
      if (user) {
        const updated = await api.updateGames(selected)
        setGames(updated)
        setSaved(updated.filter((game) => game.selected).map((game) => game.key))
        setMessage({ type: 'ok', text: '订阅已保存，活动看板会按新范围筛选。' })
      } else {
        writeLocalGames(selected)
        setGames((current) =>
          current.map((game) => ({ ...game, selected: selected.includes(game.key) })),
        )
        setSaved(selected)
        setMessage({ type: 'ok', text: '订阅已保存到本机，下次打开仍会保留。' })
      }
    } catch (ex) {
      setMessage({ type: 'error', text: ex instanceof Error ? ex.message : '保存失败' })
    } finally {
      setSaving(false)
    }
  }

  async function syncNow() {
    setSyncing(true)
    setMessage(null)
    try {
      const result = await api.sync()
      setGames(result.games)
      setMessage({
        type: 'ok',
        text: result.message ?? (result.started ? '同步已在后台开始。' : '同步正在执行或刚执行过。'),
      })
      if (result.started) {
        window.setTimeout(() => {
          api.games().then(setGames).catch(() => undefined)
        }, 5000)
        window.setTimeout(() => {
          api.games().then(setGames).catch(() => undefined)
        }, 15000)
      }
    } catch (ex) {
      setMessage({ type: 'error', text: ex instanceof Error ? ex.message : '同步失败' })
    } finally {
      setSyncing(false)
    }
  }

  async function changePassword(event: FormEvent) {
    event.preventDefault()
    setPasswordMessage(null)
    if (newPassword.length < 6) {
      setPasswordMessage({ type: 'error', text: '新密码至少 6 位' })
      return
    }
    if (newPassword !== confirmPassword) {
      setPasswordMessage({ type: 'error', text: '两次输入的新密码不一致' })
      return
    }
    setPasswordBusy(true)
    try {
      const [current_password_ciphertext, new_password_ciphertext] = await Promise.all([
        encryptPassword(currentPassword),
        encryptPassword(newPassword),
      ])
      const result = await api.changePassword({ current_password_ciphertext, new_password_ciphertext })
      setToken(result.token)
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setPasswordMessage({ type: 'ok', text: '密码已更新，其他设备上的登录已失效。' })
    } catch (ex) {
      setPasswordMessage({
        type: 'error',
        text: ex instanceof Error ? ex.message : '修改密码失败，请稍后再试',
      })
    } finally {
      setPasswordBusy(false)
    }
  }

  async function createRecoveryCode(event: FormEvent) {
    event.preventDefault()
    setRecoveryMessage(null)
    setIssuedRecoveryCode(null)
    setRecoveryBusy(true)
    try {
      const password_ciphertext = await encryptPassword(recoveryPassword)
      const result = await api.createRecoveryCode(password_ciphertext)
      setIssuedRecoveryCode(result.recovery_code)
      setRecoveryPassword('')
      setRecoveryMessage({ type: 'ok', text: '新的恢复码已生成，旧恢复码已失效。' })
    } catch (ex) {
      setRecoveryMessage({
        type: 'error',
        text: ex instanceof Error ? ex.message : '生成恢复码失败，请稍后再试',
      })
    } finally {
      setRecoveryBusy(false)
    }
  }

  async function copyRecoveryCode() {
    if (!issuedRecoveryCode) return
    try {
      await navigator.clipboard.writeText(issuedRecoveryCode)
      setRecoveryCopied(true)
      window.setTimeout(() => setRecoveryCopied(false), 1800)
    } catch {
      setRecoveryCopied(false)
    }
  }

  async function deleteAccount(event: FormEvent) {
    event.preventDefault()
    setDeleteMessage(null)
    setDeleteBusy(true)
    try {
      const password_ciphertext = await encryptPassword(deletePassword)
      await api.deleteAccount(password_ciphertext)
      onLogout()
    } catch (ex) {
      setDeleteMessage(ex instanceof Error ? ex.message : '删除账号失败，请稍后再试')
    } finally {
      setDeleteBusy(false)
    }
  }

  useReveal([loading, user?.id])

  return (
    <main className="page">
      <section className="intro">
        <h1>设置</h1>
        <p className="intro-copy">
          不登录也能浏览全部看板，并把游戏订阅保存在本机。登录后可以标记完成、保存进度，订阅会跟随账号。
        </p>
      </section>

      {message && (
        <div className={`notice-banner ${message.type}`} role="status">
          {message.text}
        </div>
      )}

      <section className="settings-block" data-reveal="pending" data-reveal-variant="fade">
        <header className="block-head">
          <div>
            <h2>外观</h2>
            <p>可以跟随系统，也可以固定使用浅色或深色。切换时会平滑过渡。</p>
          </div>
        </header>
        <div className="theme-options" role="radiogroup" aria-label="外观模式">
          {THEME_OPTIONS.map((option) => (
            <button
              key={option.key}
              type="button"
              role="radio"
              aria-checked={themeMode === option.key}
              className={`theme-option ${themeMode === option.key ? 'is-active' : ''}`}
              onClick={() => onThemeChange(option.key)}
            >
              <span className="theme-option-label">{option.label}</span>
              <span className="theme-option-hint">{option.hint}</span>
            </button>
          ))}
        </div>
      </section>

      {!user && (
        <section className="settings-block" data-reveal="pending" data-reveal-variant="fade">
          <header className="block-head">
            <div>
              <h2>登录</h2>
              <p>登录后才会显示完成勾选，并且可以按账号保存每期挑战的进度和订阅。</p>
            </div>
            <Link to="/login" state={{ from: '/settings' }} className="primary-button small" viewTransition>
              登录 / 注册
            </Link>
          </header>
        </section>
      )}

      <section className="settings-block" data-reveal="pending" data-reveal-variant="fade">
        <header className="block-head">
          <div>
            <h2>游戏订阅</h2>
            <p>
              {user
                ? '勾选要追踪的游戏，保存后同步到账号。'
                : '勾选要追踪的游戏，保存在本机浏览器，无需登录。'}
            </p>
          </div>
          <button
            type="button"
            className={`primary-button small ${!dirty ? 'is-idle' : ''}`}
            onClick={saveGames}
            disabled={saving || !dirty}
          >
            {saving ? '保存中…' : dirty ? '保存订阅' : '已保存'}
          </button>
        </header>

        <div className="game-options">
          {loading && <p className="muted-text">正在读取游戏列表…</p>}
          {games.map((game) => {
            const checked = selected.includes(game.key)
            return (
              <button
                type="button"
                key={game.key}
                className={`game-option game-${game.key} ${checked ? 'is-checked' : ''}`}
                onClick={() => toggleGame(game.key)}
                aria-pressed={checked}
              >
                <span className="game-option-main">
                  <span className="game-dot" />
                  <span className="game-option-copy">
                    <strong>{GAME_SHORT[game.key] ?? game.label}</strong>
                    <span className="game-option-meta">
                      {game.label} · v{game.sync.version ?? '—'}
                    </span>
                  </span>
                </span>
                <span className="game-option-side">
                  <span className="game-option-meta">{game.activity_count} 条活动</span>
                  <span className="game-check" aria-hidden="true">
                    ✓
                  </span>
                </span>
              </button>
            )
          })}
        </div>
      </section>

      <section className="settings-block" data-reveal="pending" data-reveal-variant="fade">
        <header className="block-head">
          <div>
            <h2>数据同步</h2>
            <p>服务端定时拉取 HoYoverse API，也可以手动触发一次。</p>
          </div>
          {user ? (
            <button type="button" className="ghost-button" onClick={syncNow} disabled={syncing}>
              {syncing ? '同步中…' : '立即同步'}
            </button>
          ) : (
            <span className="muted-text">登录后可手动同步</span>
          )}
        </header>

        <div className="sync-table">
          {games.map((game) => (
            <div className="sync-row" key={game.key}>
              <span className="sync-game">
                <span className={`game-dot game-${game.key}`} />
                {game.label}
              </span>
              <span className={`sync-state ${game.sync.status}`}>
                {game.sync.status === 'ok' ? '正常' : game.sync.status === 'error' ? '失败' : '未同步'}
              </span>
              <span className="sync-meta">v{game.sync.version ?? '—'}</span>
              <span className="sync-meta">最近成功 {formatRelative(game.sync.last_success_at)}</span>
              {game.sync.last_error && (
                <span className="sync-error" title={game.sync.last_error}>
                  {game.sync.last_error}
                </span>
              )}
            </div>
          ))}
        </div>
      </section>

      {user && (
        <section className="settings-block" data-reveal="pending" data-reveal-variant="fade">
        <header className="block-head">
          <div>
            <h2>账号</h2>
            <p>完成状态按账号保存，其他人看不到你的标记。</p>
          </div>
          <button type="button" className="text-button danger" onClick={onLogout}>
            退出登录
          </button>
        </header>
        <dl className="account-list">
          <div>
            <dt>用户名</dt>
            <dd>{user.username}</dd>
          </div>
          <div>
            <dt>用户 ID</dt>
            <dd className="mono">{user.id}</dd>
          </div>
          <div>
            <dt>订阅游戏</dt>
            <dd>{selected.map((key) => GAME_SHORT[key] ?? key).join(' · ') || '未订阅'}</dd>
          </div>
          <div>
            <dt>注册时间</dt>
            <dd>{formatRelative(user.created_at)}</dd>
          </div>
        </dl>
        <form className="password-form" onSubmit={changePassword}>
          <div className="password-form-copy">
            <h3>修改密码</h3>
            <p>
              Chrome 的密码泄露提醒由浏览器触发。换成没有在其他网站用过的密码后，登录就不会再提示。
            </p>
          </div>
          <div className="password-fields">
            <label>
              <span>当前密码</span>
              <input
                type="password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                autoComplete="current-password"
                required
              />
            </label>
            <label>
              <span>新密码</span>
              <input
                type="password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                autoComplete="new-password"
                minLength={6}
                maxLength={72}
                required
              />
            </label>
            <label>
              <span>确认新密码</span>
              <input
                type="password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                autoComplete="new-password"
                minLength={6}
                maxLength={72}
                required
              />
            </label>
            <button type="submit" className="primary-button small" disabled={passwordBusy}>
              {passwordBusy ? '更新中…' : '更新密码'}
            </button>
          </div>
          {passwordMessage && (
            <p className={`password-message ${passwordMessage.type}`} role="status">
              {passwordMessage.text}
            </p>
          )}
        </form>

        <form className="password-form" onSubmit={createRecoveryCode}>
          <div className="password-form-copy">
            <h3>恢复码</h3>
            <p>
              忘记密码时，用户名和恢复码可以重置密码。重新生成后，旧恢复码会立即失效。
            </p>
          </div>
          <div className="password-fields">
            <label>
              <span>当前密码</span>
              <input
                type="password"
                value={recoveryPassword}
                onChange={(event) => setRecoveryPassword(event.target.value)}
                autoComplete="current-password"
                required
              />
            </label>
            <button type="submit" className="ghost-button small" disabled={recoveryBusy}>
              {recoveryBusy ? '生成中…' : '生成新的恢复码'}
            </button>
          </div>
          {issuedRecoveryCode && (
            <div className="recovery-code-box">
              <code className="recovery-code-value">{issuedRecoveryCode}</code>
              <button type="button" className="ghost-button small" onClick={copyRecoveryCode}>
                {recoveryCopied ? '已复制' : '复制恢复码'}
              </button>
              <p className="muted-text">这串恢复码只显示这一次，请现在保存。</p>
            </div>
          )}
          {recoveryMessage && (
            <p className={`password-message ${recoveryMessage.type}`} role="status">
              {recoveryMessage.text}
            </p>
          )}
        </form>
        </section>
      )}

      {user && (
        <section className="settings-block danger-block" data-reveal="pending" data-reveal-variant="fade">
          <header className="block-head">
            <div>
              <h2>删除账号</h2>
              <p>删除后，订阅、恢复码和已完成记录都会一起清除，无法恢复。</p>
            </div>
            {!deleteOpen && (
              <button type="button" className="text-button danger" onClick={() => setDeleteOpen(true)}>
                删除账号
              </button>
            )}
          </header>
          {deleteOpen && (
            <form className="password-form" onSubmit={deleteAccount}>
              <div className="password-fields">
                <label>
                  <span>输入密码确认</span>
                  <input
                    type="password"
                    value={deletePassword}
                    onChange={(event) => setDeletePassword(event.target.value)}
                    autoComplete="current-password"
                    required
                  />
                </label>
                <button type="submit" className="primary-button small danger" disabled={deleteBusy}>
                  {deleteBusy ? '删除中…' : '确认永久删除'}
                </button>
                <button
                  type="button"
                  className="ghost-button small"
                  onClick={() => {
                    setDeleteOpen(false)
                    setDeletePassword('')
                    setDeleteMessage(null)
                  }}
                >
                  取消
                </button>
              </div>
              {deleteMessage && <p className="password-message error" role="alert">{deleteMessage}</p>}
            </form>
          )}
        </section>
      )}

      <section className="source-note">
        <h2>数据来源</h2>
        <p>
          活动、挑战、卡池与兑换码数据来自{' '}
          <a href="https://github.com/torikushiii/hoyoverse-api" target="_blank" rel="noreferrer">
            torikushiii/hoyoverse-api
          </a>
          。本服务只做同步与缓存，不修改原始数据；「已完成」是本系统自己的字段。
        </p>
      </section>
    </main>
  )
}
