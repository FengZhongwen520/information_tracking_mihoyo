import type { Activity } from '../types'
import { challengePeriodName, challengeTone, type ChallengeGroup } from '../lib/board'
import { countdownInfo, formatTimestamp, GAME_SHORT } from '../lib/format'
import { RemoteImage } from './RemoteImage'
import { ImagePlaceholder } from './ImagePlaceholder'

interface Props {
  groups: ChallengeGroup[]
  busyId: string | null
  canEdit: boolean
  onToggle: (activity: Activity) => void
}

export function ChallengeBoard({ groups, busyId, canEdit, onToggle }: Props) {
  if (groups.length === 0) return null

  const byGame = new Map<string, ChallengeGroup[]>()
  for (const group of groups) {
    const list = byGame.get(group.game)
    if (list) list.push(group)
    else byGame.set(group.game, [group])
  }

  return (
    <>
      {[...byGame.entries()].map(([game, list]) => (
        <section className="challenge-board" key={game} aria-label="挑战关卡汇总">
          <header className="challenge-board-head">
            <div>
              <h2>挑战关卡</h2>
              <p>{GAME_SHORT[game] ?? game} · 按周期汇总。完成状态只作用于当期。</p>
            </div>
            <span className="challenge-count">{list.length} 类</span>
          </header>

          <div className="challenge-grid">
            {list.map((group) => {
              const current = group.current
              const info = countdownInfo(current.start_at, current.end_at)
              const tone = challengeTone(group)
              const busy = busyId === current.id
              const reference = group.next ?? group.previous
              const referenceLabel = group.next ? '下期结束' : group.previous ? '上期结束' : '下期结束'
              const cover = current.cover?.trim()
              const rewardIcons = (current.rewards ?? []).filter((reward) => reward.icon).slice(0, 5)
              const rewardTotal = (current.rewards ?? []).filter((reward) => reward.icon).length
              return (
                <article
                  key={group.key}
                  className={`challenge-card game-${group.game} tone-${tone} ${current.completed ? 'is-completed' : ''} ${cover ? 'has-media' : ''}`}
                  data-reveal="pending"
                  data-reveal-variant="card"
                >
                  {cover && (
                    <RemoteImage
                      className="challenge-media"
                      src={cover}
                      alt=""
                      width={640}
                      loading="eager"
                      fallback={<ImagePlaceholder className="challenge-media" compact />}
                    />
                  )}
                  {cover && <div className="challenge-scrim" aria-hidden="true" />}
                  <div className="challenge-top">
                    <span className="challenge-stage">
                      <span className="game-dot" />
                      {group.stage}
                    </span>
                    {canEdit && (
                      <button
                        type="button"
                        className={`check-button challenge-check ${current.completed ? 'is-done' : ''}`}
                        aria-pressed={current.completed}
                        aria-label={current.completed ? `取消当期完成：${group.stage}` : `标记当期完成：${group.stage}`}
                        title={current.completed ? '取消当期完成' : '标记当期完成'}
                        disabled={busy}
                        onClick={() => onToggle(current)}
                      >
                        <span className="check-circle">{busy ? '…' : '✓'}</span>
                      </button>
                    )}
                  </div>

                  <p className="challenge-period">{challengePeriodName(current)}</p>
                  <p className="challenge-countdown">{current.completed ? '已完成' : info.text}</p>

                  {rewardIcons.length > 0 && (
                    <div className="challenge-rewards" aria-label="当期奖励">
                      {rewardIcons.map((reward) => (
                        <span
                          className="challenge-reward"
                          title={`${reward.name}${reward.amount && reward.amount > 0 ? ` ×${reward.amount}` : ''}`}
                          key={`${reward.name}-${reward.amount}`}
                        >
                          <RemoteImage
                            src={reward.icon}
                            alt={reward.name}
                            fallback={<span aria-hidden="true">{reward.name.trim().slice(0, 1)}</span>}
                          />
                        </span>
                      ))}
                      {rewardTotal > rewardIcons.length && (
                        <span className="challenge-reward-more">+{rewardTotal - rewardIcons.length}</span>
                      )}
                    </div>
                  )}

                  <div className="challenge-dates">
                    <div className="challenge-date">
                      <span>当期结束</span>
                      <strong>{formatTimestamp(current.end_at)}</strong>
                    </div>
                    <div className="challenge-date">
                      <span>{referenceLabel}</span>
                      <strong>{reference ? formatTimestamp(reference.end_at) : '—'}</strong>
                    </div>
                  </div>
                </article>
              )
            })}
          </div>
        </section>
      ))}
    </>
  )
}
