import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { formatTimestamp, GAME_SHORT, STATUS_LABEL } from '../lib/format'
import { useReveal } from '../lib/reveal'
import { selectedGamesFor, useLocalGames } from '../lib/subscriptions'
import { RemoteImage } from '../components/RemoteImage'
import { SyncNotice } from '../components/SyncNotice'
import { useLiveRefresh } from '../lib/useLiveRefresh'
import type { Banner, BannerItem, Game, User } from '../types'

interface Props {
  user: User | null
}

const GENERIC_BANNERS = new Set([
  '角色活动跃迁',
  '光锥活动跃迁',
  '角色活动调频',
  '音擎活动调频',
])

function featured(items: BannerItem[]): BannerItem[] {
  return [...items].sort((left, right) => {
    const rank = (item: BannerItem) => (item.rarity === '5' || item.rarity === 'S' ? 0 : 1)
    return rank(left) - rank(right)
  })
}

function rarityTone(rarity: string | null): string {
  if (rarity === '5' || rarity === 'S') return '5'
  if (rarity === '4' || rarity === 'A') return '4'
  return 'unknown'
}

function BannerCard({ banner }: { banner: Banner }) {
  const picks = featured(banner.items)
  const generic = GENERIC_BANNERS.has(banner.name)
  const title = generic && picks.length > 0
    ? picks.slice(0, 2).map((item) => item.name).join(' · ')
    : banner.name
  const shownItems = picks

  return (
    <article className={`banner-card game-${banner.game}`} data-reveal="pending" data-reveal-variant="card">
      <div className="banner-top">
        <span className="banner-game">
          <span className="game-dot" />
          {GAME_SHORT[banner.game] ?? banner.game_label}
        </span>
        <span className="banner-state">{STATUS_LABEL[banner.status] ?? banner.status}</span>
      </div>

      <h3>{title}</h3>
      <div className="banner-meta">
        <span>{banner.banner_type}</span>
        {banner.version && <span>v{banner.version}</span>}
      </div>

      {shownItems.length > 0 && (
        <ul className="banner-picks" aria-label="本期角色与装备">
          {shownItems.map((item) => (
            <li
              className={`banner-pick rarity-${rarityTone(item.rarity)}`}
              key={`${banner.id}-${item.name}`}
              title={item.name}
            >
              <span className="banner-pick-frame">
                <RemoteImage
                  src={item.icon}
                  alt={item.name}
                  fallback={<span className="banner-pick-fallback" aria-hidden="true">{item.name.trim().slice(0, 1)}</span>}
                />
              </span>
              <span className="banner-pick-name">{item.name}</span>
            </li>
          ))}
        </ul>
      )}

      <p className="banner-time">至 {formatTimestamp(banner.end_at)}</p>
    </article>
  )
}

export function Banners({ user }: Props) {
  const [games, setGames] = useState<Game[]>([])
  const [banners, setBanners] = useState<Banner[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const localGames = useLocalGames()

  async function load(silent = false) {
    if (!silent) setLoading(true)
    try {
      const [gameList, bannerList] = await Promise.all([api.games(), api.banners()])
      setGames(gameList)
      setBanners(bannerList)
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

  useReveal([loading, banners.length])

  return (
    <main className="page extras-page">
      <section className="intro">
        <h1>卡池</h1>
        <p className="intro-copy">
          当前与即将开放的跃迁、调频信息，按游戏分组。订阅范围可在设置页调整，无需登录。
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
        <div className="empty-panel" key="loading">正在读取卡池…</div>
      ) : selectedGames.length === 0 ? (
        <div className="empty-panel" key="no-games">
          <strong>还没有订阅任何游戏</strong>
          <p>去设置页勾选原神、绝区零或星铁，卡池会自动出现在这里。</p>
          <Link to="/settings" className="primary-button inline-button" viewTransition>
            去设置订阅
          </Link>
        </div>
      ) : (
        <div className="extras-stack" key="list">
          {selectedGames.map((game) => {
            const gameBanners = banners.filter((item) => item.game === game.key)
            return (
              <section
                id={`banners-${game.key}`}
                className={`game-extras game-${game.key}`}
                key={game.key}
                aria-label={`${game.label} 卡池`}
              >
                <header className="game-extras-head">
                  <div className="game-extras-title">
                    <span className="game-dot" />
                    <h2>{GAME_SHORT[game.key] ?? game.label}</h2>
                  </div>
                  <span>{gameBanners.length > 0 ? `${gameBanners.length} 个卡池` : '暂无数据'}</span>
                </header>

                {gameBanners.length === 0 ? (
                  <p className="extras-empty">暂无卡池信息。</p>
                ) : (
                  <div className="banner-grid">
                    {gameBanners.map((banner) => (
                      <BannerCard banner={banner} key={banner.id} />
                    ))}
                  </div>
                )}
              </section>
            )
          })}
        </div>
      )}
    </main>
  )
}
