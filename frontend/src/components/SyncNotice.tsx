import { formatRelative, GAME_SHORT } from '../lib/format'
import type { Game } from '../types'

interface Props {
  games: Game[]
}

export function SyncNotice({ games }: Props) {
  const now = Date.now()
  const stale = games.filter((game) => {
    if (!game.selected) return false
    if (game.sync.status === 'error') return true
    if (!game.sync.last_success_at) return true
    const lastSuccess = new Date(game.sync.last_success_at).getTime()
    return Number.isFinite(lastSuccess) && now - lastSuccess > 2 * 60 * 60 * 1000
  })

  if (stale.length === 0) return null

  return (
    <div className="sync-notice" role="status">
      <strong>数据可能不是最新</strong>
      <span>
        {stale
          .map((game) => {
            const name = GAME_SHORT[game.key] ?? game.label
            return game.sync.status === 'error'
              ? `${name}同步失败`
              : `${name}上次成功 ${formatRelative(game.sync.last_success_at)}`
          })
          .join(' · ')}
      </span>
    </div>
  )
}
