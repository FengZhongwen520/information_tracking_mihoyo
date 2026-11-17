import { FormEvent, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { api, setToken } from '../api'
import { encryptPassword } from '../lib/crypto'
import type { User } from '../types'

interface Props {
  onAuthed: (user: User) => void
}

type Mode = 'login' | 'register' | 'reset'

export function Login({ onAuthed }: Props) {
  const navigate = useNavigate()
  const location = useLocation()
  const from = typeof location.state?.from === 'string' ? location.state.from : '/'
  const [mode, setMode] = useState<Mode>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [recoveryCode, setRecoveryCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [issuedCode, setIssuedCode] = useState<string | null>(null)
  const [pendingUser, setPendingUser] = useState<User | null>(null)
  const [copied, setCopied] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setNotice(null)
    if ((mode === 'register' || mode === 'reset') && password !== confirm) {
      setError('两次输入的密码不一致')
      return
    }
    setBusy(true)
    try {
      const password_ciphertext = await encryptPassword(password)
      if (mode === 'login') {
        const result = await api.login({ username: username.trim(), password_ciphertext })
        setToken(result.token)
        onAuthed(result.user)
        navigate(from, { replace: true, viewTransition: true })
        return
      }
      if (mode === 'register') {
        const result = await api.register({ username: username.trim(), password_ciphertext })
        setToken(result.token)
        if (result.recovery_code) {
          setPendingUser(result.user)
          setIssuedCode(result.recovery_code)
        } else {
          onAuthed(result.user)
          navigate(from, { replace: true, viewTransition: true })
        }
        return
      }

      await api.resetPassword({
        username: username.trim(),
        recovery_code: recoveryCode.trim(),
        new_password_ciphertext: password_ciphertext,
      })
      setMode('login')
      setPassword('')
      setConfirm('')
      setRecoveryCode('')
      setNotice('密码已重置，请用新密码登录。')
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : '操作失败，请稍后再试')
    } finally {
      setBusy(false)
    }
  }

  function switchMode(next: Mode) {
    setMode(next)
    setError(null)
    setNotice(null)
    setConfirm('')
    setRecoveryCode('')
  }

  async function copyRecoveryCode() {
    if (!issuedCode) return
    try {
      await navigator.clipboard.writeText(issuedCode)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    } catch {
      setCopied(false)
    }
  }

  if (issuedCode) {
    return (
      <main className="auth-page">
        <section className="auth-pitch">
          <h1>账号已经建好</h1>
          <p className="intro-copy">
            下面这串恢复码只显示这一次。忘记密码时，它和用户名一起用来重置密码。
          </p>
        </section>

        <section className="auth-card">
          <div className="auth-heading">
            <h2>保存你的恢复码</h2>
            <p>建议截图或存进密码管理器，不要发给别人。</p>
          </div>
          <code className="recovery-code-value">{issuedCode}</code>
          <div className="recovery-code-actions">
            <button type="button" className="ghost-button small" onClick={copyRecoveryCode}>
              {copied ? '已复制' : '复制恢复码'}
            </button>
            <button
              type="button"
              className="primary-button small"
              onClick={() => {
                if (pendingUser) onAuthed(pendingUser)
                navigate(from, { replace: true, viewTransition: true })
              }}
            >
              我已保存，进入看板
            </button>
          </div>
          <p className="auth-foot">之后也可以在设置页重新生成恢复码。</p>
        </section>
      </main>
    )
  }

  return (
    <main className="auth-page">
      <section className="auth-pitch">
        <h1>原神、绝区零、星铁</h1>
        <p className="intro-copy">
          服务端从 HoYoverse API 同步活动、卡池与兑换码。登录后，完成记录只保存在你的账号下。
        </p>
        <ul className="pitch-games">
          <li>
            <span className="game-dot game-ys" />
            原神
          </li>
          <li>
            <span className="game-dot game-zzz" />
            绝区零
          </li>
          <li>
            <span className="game-dot game-sr" />
            崩坏：星穹铁道
          </li>
        </ul>
      </section>

      <section className="auth-card">
        <div className="auth-heading">
          <h2>{mode === 'reset' ? '重置密码' : '今天也在米哈打工'}</h2>
          <p>{mode === 'reset' ? '用注册时保存的恢复码设置新密码。' : '完成记录按账号保存。'}</p>
        </div>

        {mode !== 'reset' && (
          <div className="auth-tabs" role="tablist" aria-label="登录或注册">
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'login'}
              className={mode === 'login' ? 'is-active' : ''}
              onClick={() => switchMode('login')}
            >
              登录
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'register'}
              className={mode === 'register' ? 'is-active' : ''}
              onClick={() => switchMode('register')}
            >
              注册
            </button>
          </div>
        )}

        <form className="auth-form" onSubmit={submit}>
          <label>
            <span>用户名</span>
            <input
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
              minLength={3}
              maxLength={24}
              placeholder="3-24 位，字母 / 数字 / 中文"
              required
            />
          </label>
          {mode === 'reset' && (
            <label>
              <span>恢复码</span>
              <input
                value={recoveryCode}
                onChange={(event) => setRecoveryCode(event.target.value)}
                autoComplete="one-time-code"
                placeholder="XXXX-XXXX-XXXX-XXXX"
                required
              />
            </label>
          )}
          <label>
            <span>{mode === 'reset' ? '新密码' : '密码'}</span>
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              minLength={6}
              maxLength={72}
              placeholder="至少 6 位"
              required
            />
          </label>
          {mode !== 'login' && (
            <label>
              <span>确认密码</span>
              <input
                type="password"
                value={confirm}
                onChange={(event) => setConfirm(event.target.value)}
                autoComplete="new-password"
                minLength={6}
                maxLength={72}
                placeholder="再输入一次"
                required
              />
            </label>
          )}

          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          {notice && (
            <p className="form-notice" role="status">
              {notice}
            </p>
          )}

          <button type="submit" className="primary-button" disabled={busy}>
            {busy ? '处理中…' : mode === 'login' ? '登录' : mode === 'register' ? '创建账号' : '重置密码'}
          </button>
        </form>

        {mode === 'login' && (
          <p className="auth-foot auth-foot-secondary">
            <button type="button" className="inline-toggle" onClick={() => switchMode('reset')}>
              忘记密码？
            </button>
          </p>
        )}

        <p className="auth-foot">
          {mode === 'login' && '还没有账号？'}
          {mode === 'register' && '已经有账号？'}
          {mode === 'reset' && '想起来了？'}
          <button
            type="button"
            className="inline-toggle"
            onClick={() => switchMode(mode === 'login' ? 'register' : 'login')}
          >
            {mode === 'login' ? '立即注册' : mode === 'register' ? '去登录' : '返回登录'}
          </button>
        </p>
      </section>
    </main>
  )
}
