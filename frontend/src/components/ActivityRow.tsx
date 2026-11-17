import type { Activity } from '../types'
import {
  countdownInfo,
  extractRewards,
  formatDate,
  formatRange,
  formatRewards,
  GAME_SHORT,
  remainingFraction,
} from '../lib/format'
import { RemoteImage } from './RemoteImage'
import { ImagePlaceholder } from './ImagePlaceholder'
import { StatusBadge } from './StatusBadge'

interface Props {
  activity: Activity
  busy: boolean
  canEdit: boolean
  onToggle: (activity: Activity) => void
}

export function ActivityRow({ activity, busy, canEdit, onToggle }: Props) {
  const cover = activity.cover?.trim()
  const short = GAME_SHORT[activity.game] ?? activity.game_label
  const info = countdownInfo(activity.start_at, activity.end_at)
  const tone = activity.completed ? 'done' : info.tone
  const fraction = activity.completed ? 1 : remainingFraction(activity.start_at, activity.end_at)
  const rewards = formatRewards(activity.rewards) ?? extractRewards(activity.description)
  const rewardItems = (activity.rewards ?? [])
    .filter((reward) => reward.name?.trim())
    .slice(0, 3)
  const showRewardIcons = rewardItems.some((reward) => reward.icon)
  const urgentLabel = !activity.completed && info.tone === 'critical' ? '今日截止' : undefined
  const remainingLabel = activity.completed
    ? `完成于 ${formatDate(activity.completed_at)}`
    : info.tone === 'ended'
      ? '活动已结束'
      : info.tone === 'upcoming'
        ? '距开始'
        : '剩余'

  return (
    <article
      className={`activity-row game-${activity.game} tone-${tone} ${activity.completed ? 'is-completed' : ''}`}
      data-reveal="pending"
      data-reveal-variant="row"
    >
      <div className="row-media-frame">
        <RemoteImage
          className="row-media"
          src={cover}
          alt=""
          width={640}
          loading="eager"
          fallback={<ImagePlaceholder className="row-media is-fallback" />}
        />
      </div>

      <div className="row-main">
        <div className="row-title">
          <span className="row-game">
            <span className="game-dot" />
            {short}
          </span>
          <h3>{activity.name}</h3>
          <StatusBadge status={activity.status} completed={activity.completed} label={urgentLabel} />
          {(activity.version || activity.kind) && (
            <span className="row-tag">{activity.version ? `v${activity.version}` : activity.kind}</span>
          )}
          {canEdit && (
            <button
              type="button"
              className={`check-button row-check ${activity.completed ? 'is-done' : ''}`}
              aria-pressed={activity.completed}
              aria-label={activity.completed ? '取消完成' : '标记完成'}
              title={activity.completed ? '取消完成' : '标记完成'}
              disabled={busy}
              onClick={() => onToggle(activity)}
            >
              <span className="check-circle">{busy ? '…' : '✓'}</span>
            </button>
          )}
        </div>

        {showRewardIcons ? (
          <ul className="row-rewards" aria-label="活动奖励">
            {rewardItems.map((reward) => (
              <li className="row-reward-chip" key={`${reward.name}-${reward.amount}`}>
                <RemoteImage
                  className="row-reward-icon"
                  src={reward.icon}
                  alt=""
                  fallback={<span className="row-reward-initial" aria-hidden="true">{reward.name.trim().slice(0, 1)}</span>}
                />
                <span>
                  {reward.name.trim()}
                  {reward.amount && reward.amount > 0 ? ` ×${reward.amount}` : ''}
                </span>
              </li>
            ))}
          </ul>
        ) : rewards ? (
          <p className="row-reward">{rewards}</p>
        ) : activity.description ? (
          <p className="row-note">{activity.description}</p>
        ) : null}

        <p className="row-time">{formatRange(activity.start_at, activity.end_at)}</p>
      </div>

      <div className="row-side">
        <div className="row-countdown">{activity.completed ? '已完成' : info.text}</div>
        <div className="row-remaining">{remainingLabel}</div>
        <div className="row-bar" aria-hidden="true">
          <span style={{ transform: `scaleX(${fraction})` }} />
        </div>
      </div>
    </article>
  )
}
