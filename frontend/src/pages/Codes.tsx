import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { SyncNotice } from '../components/SyncNotice'
import { formatReward, GAME_SHORT } from '../lib/format'
import { useReveal } from '../lib/reveal'
import { useLiveRefresh } from '../lib/useLiveRefresh'
import { selectedGamesFor, useLocalGames } from '../lib/subscriptions'
import type { Code, Game, User } from '../types'

interface Props {
  user: User | null
}

async function writeClipboard(value: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value)
      return true
    }
  } catch {
    // fall through to the legacy path
  }
  try {
    const area = document.createElement('textarea')
    area.value = value
    area.setAttribute('readonly', '')
    area.style.position = 'fixed'
    area.style.opacity = '0'
    document.body.appendChild(area)
    area.select()
    const ok = document.execCommand('copy')
    area.remove()
    return ok
  } catch {
    return false
  }
}

export function Codes({ user }: Props) {
  const [games, setGames] = useState<Game[]>([])
  const [codes, setCodes] = useState<Code[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState<string | null>(null)
  const [failed, setFailed] = useState<string | null>(null)
  const localGames = useLocalGames()

  async function load(silent = false) {
    if (!silent) setLoading(true)
    try {
      const [gameList, codeList] = await Promise.all([api.games(), api.codes()])
      setGames(gameList)
      setCodes(codeList)
      setError(null)
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : '加载失败')
    } finally {
      if (!silent) setLoading(false)
    }
  }

  useLiveRefresh(load)

  const selectedGames = useMemo(
    () => selectedGamesFor(games, user, localGames),
    [games, user, localGames],
  )

  useReveal([loading, codes.length])

  async function copy(value: string) {
    if (await writeClipboard(value)) {
      setCopied(value)
      setFailed(null)
      window.setTimeout(() => setCopied((current) => (current === value ? null : current)), 1800)
    } else {
      setCopied(null)
      setFailed(value)
      window.setTimeout(() => setFailed((current) => (current === value ? null : current)), 1800)
    }
  }

  return (
    <main className="page extras-page">
      <section className="intro">
        <h1>兑换码</h1>
        <p className="intro-copy">
          按游戏分组，点一下即可复制。订阅范围可在设置页调整，无需登录。
        </p>
      </section>

      {error && (
        <div className="error-banner" role="alert">
          <strong>操作未完成</strong>
          <span>{error}</span>
          <button type="button" onClick={() => setError(null)} aria-label="关闭提示">
            ×
          </button>
        </div>
      )}

      <SyncNotice games={games} />

      {loading ? (
        <div className="empty-panel" key="loading">正在读取兑换码…</div>
      ) : selectedGames.length === 0 ? (
        <div className="empty-panel" key="no-games">
          <strong>还没有订阅任何游戏</strong>
          <p>去设置页勾选原神、绝区零或星铁，兑换码会自动出现在这里。</p>
          <Link to="/settings" className="primary-button inline-button" viewTransition>
            去设置订阅
          </Link>
        </div>
      ) : (
        <div className="extras-stack" key="list">
          {selectedGames.map((game) => {
            const gameCodes = codes.filter((item) => item.game === game.key)
            return (
              <section
                id={`codes-${game.key}`}
                className={`game-extras game-${game.key}`}
                key={game.key}
                aria-label={`${game.label} 兑换码`}
              >
                <header className="game-extras-head">
                  <div className="game-extras-title">
                    <span className="game-dot" />
                    <h2>{GAME_SHORT[game.key] ?? game.label}</h2>
                  </div>
                  <span>{gameCodes.length} 个兑换码</span>
                </header>

                {gameCodes.length === 0 ? (
                  <p className="extras-empty">暂无可用兑换码。</p>
                ) : (
                  <ul className="code-grid">
                    {gameCodes.map((item) => (
                      <li className="code-card" key={item.id} data-reveal="pending" data-reveal-variant="compact">
                        <div className="code-card-main">
                          <code>{item.code}</code>
                          <button
                            type="button"
                            className={`copy-button ${copied === item.code ? 'is-copied' : ''} ${failed === item.code ? 'is-failed' : ''}`}
                            onClick={() => void copy(item.code)}
                            aria-label={`复制兑换码 ${item.code}`}
                          >
                            {copied === item.code ? '已复制' : failed === item.code ? '复制失败' : '复制'}
                          </button>
                        </div>
                        {item.rewards.length > 0 && (
                          <p className="code-reward">{item.rewards.map(formatReward).join(' · ')}</p>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            )
          })}
        </div>
      )}
    </main>
  )
}
