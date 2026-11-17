import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { ActivityRow } from '../components/ActivityRow'
import { SyncNotice } from '../components/SyncNotice'
import { SORT_OPTIONS, sortActivities, type SortMode } from '../lib/board'
import { useReveal } from '../lib/reveal'
import { useLiveRefresh } from '../lib/useLiveRefresh'
import { selectedGamesFor, useLocalGames } from '../lib/subscriptions'
import type { Activity, Game, User } from '../types'

type StatusFilter = 'all' | 'active' | 'ending' | 'upcoming' | 'ended'

const STATUS_FILTERS: { key: StatusFilter; label: string }[] = [
  { key: 'all', label: '全部状态' },
  { key: 'active', label: '进行中' },
  { key: 'ending', label: '即将结束' },
  { key: 'upcoming', label: '未开始' },
  { key: 'ended', label: '已结束' },
]

interface Props {
  user: User | null
}

export function Dashboard({ user }: Props) {
  const [games, setGames] = useState<Game[]>([])
  const [activities, setActivities] = useState<Activity[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all')
  const [hideCompleted, setHideCompleted] = useState(true)
  const [sortMode, setSortMode] = useState<SortMode>('urgent')
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)
  const canEdit = Boolean(user)
  const localGames = useLocalGames()

  async function load(silent = false) {
    if (!silent) setLoading(true)
    try {
      const [gameList, activityList] = await Promise.all([
        api.games(),
        api.activities(),
      ])
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

  const board = useMemo(() => {
    const rows = activities.filter((item) => {
      if (!selectedKeys.has(item.game)) return false
      if (item.challenge_stage) return false
      if (canEdit && hideCompleted && item.completed) return false
      if (statusFilter === 'active' && item.status !== 'active' && item.status !== 'ending') return false
      if (statusFilter !== 'all' && statusFilter !== 'active' && item.status !== statusFilter) return false
      return true
    })

    return { rows: sortActivities(rows, sortMode) }
  }, [activities, statusFilter, hideCompleted, sortMode, canEdit, selectedKeys])

  useReveal([loading, activities.length, statusFilter, hideCompleted, sortMode])

  return (
    <main className="page">
      <section className="intro">
        <h1>活动看板</h1>
        <p className="intro-copy">
          三款游戏的常规活动都在这里。挑战关卡、兑换码和卡池已经拆成单独页面。
          {!canEdit && ' 登录后可以标记完成。'}
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
          aria-controls="board-filters"
          onClick={() => setFiltersOpen((open) => !open)}
        >
          筛选
          <span className={`filter-caret ${filtersOpen ? 'is-open' : ''}`} aria-hidden="true" />
        </button>
        <div id="board-filters" className={`filter-panel ${filtersOpen ? 'is-open' : ''}`}>
          <div className="status-line">
            <nav className="filter-tabs" aria-label="按状态筛选" data-lenis-prevent-touch>
              {STATUS_FILTERS.map((item) => (
                <button
                  type="button"
                  key={item.key}
                  className={statusFilter === item.key ? 'is-active' : ''}
                  onClick={() => setStatusFilter(item.key)}
                >
                  {item.label}
                </button>
              ))}
            </nav>
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
        <div className="empty-panel" key="loading">正在读取活动数据…</div>
      ) : selectedGames.length === 0 ? (
        <div className="empty-panel" key="no-games">
          <strong>还没有订阅任何游戏</strong>
          <p>去设置页勾选原神、绝区零或星铁，活动会自动出现在这里。</p>
          <Link to="/settings" className="primary-button inline-button" viewTransition>
            去设置订阅
          </Link>
        </div>
      ) : board.rows.length === 0 ? (
        <div className="empty-panel" key="no-results">
          <strong>没有符合条件的活动</strong>
          <p>调整筛选条件，或重新同步数据。</p>
        </div>
      ) : (
        <div className="activity-list" key="list">
          {board.rows.map((activity) => (
            <ActivityRow
              key={activity.id}
              activity={activity}
              busy={busyId === activity.id}
              canEdit={canEdit}
              onToggle={toggle}
            />
          ))}
        </div>
      )}

    </main>
  )
}
