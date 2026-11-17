import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { ChallengeBoard } from '../components/ChallengeBoard'
import { SyncNotice } from '../components/SyncNotice'
import {
  buildChallengeGroups,
  challengeKey,
  SORT_OPTIONS,
  type SortMode,
} from '../lib/board'
import { useReveal } from '../lib/reveal'
import { useLiveRefresh } from '../lib/useLiveRefresh'
import { selectedGamesFor, useLocalGames } from '../lib/subscriptions'
import type { Activity, Game, User } from '../types'

interface Props {
  user: User | null
}

export function Challenges({ user }: Props) {
  const [games, setGames] = useState<Game[]>([])
  const [activities, setActivities] = useState<Activity[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [hideCompleted, setHideCompleted] = useState(true)
  const [sortMode, setSortMode] = useState<SortMode>('urgent')
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)
  const canEdit = Boolean(user)
  const localGames = useLocalGames()

  async function load(silent = false) {
    if (!silent) setLoading(true)
    try {
      const [gameList, activityList] = await Promise.all([api.games(), api.activities()])
      setGames(gameList)
      setActivities(activityList)
      setError(null)
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : '加载失败')
    } finally {
      if (!silent) setLoading(false)
    }
  }

  useLiveRefresh(load)

  async function toggle(activity: Activity) {
    if (!canEdit) return
    const next = !activity.completed
    setBusyId(activity.id)
    setActivities((current) =>
      current.map((item) =>
        item.id === activity.id
          ? { ...item, completed: next, completed_at: next ? new Date().toISOString() : null }
          : item,
      ),
    )
    try {
      const updated = await api.setCompleted(activity.id, next)
      setActivities((current) => current.map((item) => (item.id === updated.id ? updated : item)))
    } catch (ex) {
      setActivities((current) =>
        current.map((item) =>
          item.id === activity.id
            ? { ...item, completed: activity.completed, completed_at: activity.completed_at }
            : item,
        ),
      )
      setError(ex instanceof Error ? ex.message : '保存完成状态失败')
    } finally {
      setBusyId(null)
    }
  }

  const selectedGames = useMemo(
    () => selectedGamesFor(games, user, localGames),
    [games, user, localGames],
  )
  const selectedKeys = useMemo(() => new Set(selectedGames.map((game) => game.key)), [selectedGames])
  const visibleActivities = useMemo(
    () => activities.filter((item) => selectedKeys.has(item.game)),
    [activities, selectedKeys],
  )

  const groups = useMemo(() => {
    const keys = new Set<string>()
    for (const item of visibleActivities) {
      const key = challengeKey(item)
      if (key) keys.add(key)
    }
    const built = buildChallengeGroups(visibleActivities, keys, sortMode)
    return canEdit && hideCompleted
      ? built.filter((group) => !group.current.completed)
      : built
  }, [visibleActivities, sortMode, hideCompleted, canEdit])

  useReveal([loading, activities.length, hideCompleted, sortMode])

  return (
    <main className="page">
      <section className="intro">
        <h1>挑战看板</h1>
        <p className="intro-copy">
          虚构叙事、混沌回忆、异相仲裁、末日幻影与幽境危战按周期汇总。每张卡显示当期和相邻一期的结束时间。
          {!canEdit && ' 登录后可以标记当期完成。'}
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

      <div className="filter-region">
        <button
          type="button"
          className="text-button filter-toggle"
          aria-expanded={filtersOpen}
          aria-controls="challenge-filters"
          onClick={() => setFiltersOpen((open) => !open)}
        >
          筛选
          <span className={`filter-caret ${filtersOpen ? 'is-open' : ''}`} aria-hidden="true" />
        </button>
        <div id="challenge-filters" className={`filter-panel ${filtersOpen ? 'is-open' : ''}`}>
          <div className="status-line">
            <span className="muted-text">{groups.length} 类挑战</span>
            <div className="board-tools">
              <label className="sort-select">
                <span>排序</span>
                <select
                  value={sortMode}
                  onChange={(event) => setSortMode(event.target.value as SortMode)}
                  aria-label="排序方式"
                >
                  {SORT_OPTIONS.map((option) => (
                    <option key={option.key} value={option.key}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              {canEdit && (
                <label className="check-inline">
                  <input
                    type="checkbox"
                    checked={hideCompleted}
                    onChange={(event) => setHideCompleted(event.target.checked)}
                  />
                  <span className="switch-track" aria-hidden="true">
                    <span />
                  </span>
                  <span>仅看未完成</span>
                </label>
              )}
            </div>
          </div>
        </div>
      </div>

      {loading ? (
        <div className="empty-panel" key="loading">正在读取挑战数据…</div>
      ) : selectedGames.length === 0 ? (
        <div className="empty-panel" key="no-games">
          <strong>还没有订阅任何游戏</strong>
          <p>去设置页勾选原神、绝区零或星铁，挑战会自动出现在这里。</p>
          <Link to="/settings" className="primary-button inline-button" viewTransition>
            去设置订阅
          </Link>
        </div>
      ) : groups.length === 0 ? (
        <div className="empty-panel" key="no-results">
          <strong>没有符合条件的挑战</strong>
          <p>关闭「仅看未完成」，或等待下一轮数据同步。</p>
        </div>
      ) : (
        <ChallengeBoard groups={groups} busyId={busyId} canEdit={canEdit} onToggle={toggle} />
      )}
    </main>
  )
}
