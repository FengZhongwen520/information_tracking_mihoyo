import type { Activity } from '../types'
import { countdownInfo } from './format'

export type SortMode = 'urgent' | 'latestStart'

export const SORT_OPTIONS: { key: SortMode; label: string }[] = [
  { key: 'urgent', label: '时间越紧越靠前' },
  { key: 'latestStart', label: '活动最新开始时间' },
]

export interface ChallengeGroup {
  key: string
  stage: string
  game: string
  current: Activity
  next: Activity | null
  previous: Activity | null
}

const endValue = (activity: Activity) => {
  const value = activity.end_at ? new Date(activity.end_at).getTime() : Number.NaN
  return Number.isNaN(value) ? Number.POSITIVE_INFINITY : value
}

const startValue = (activity: Activity) => {
  const value = activity.start_at ? new Date(activity.start_at).getTime() : Number.NaN
  return Number.isNaN(value) ? Number.NEGATIVE_INFINITY : value
}

const compare = (left: number, right: number) => (left === right ? 0 : left < right ? -1 : 1)
const endedRank = (activity: Activity) => (activity.status === 'ended' ? 1 : 0)

export function sortActivities(list: Activity[], mode: SortMode): Activity[] {
  return [...list].sort((left, right) => {
    const ended = compare(endedRank(left), endedRank(right))
    if (ended !== 0) return ended
    if (mode === 'latestStart') {
      return (
        compare(startValue(right), startValue(left))
        || compare(endValue(left), endValue(right))
        || left.name.localeCompare(right.name)
      )
    }
    return (
      compare(endValue(left), endValue(right))
      || compare(startValue(left), startValue(right))
      || left.name.localeCompare(right.name)
    )
  })
}

export const challengeKey = (activity: Activity) =>
  activity.challenge_stage ? `${activity.game}:${activity.challenge_stage}` : null

export function buildChallengeGroups(
  all: Activity[],
  keys: Set<string>,
  mode: SortMode,
): ChallengeGroup[] {
  const buckets = new Map<string, Activity[]>()
  for (const activity of all) {
    const key = challengeKey(activity)
    if (!key || !keys.has(key) || !activity.challenge_stage) continue
    const bucket = buckets.get(key)
    if (bucket) bucket.push(activity)
    else buckets.set(key, [activity])
  }

  const groups: ChallengeGroup[] = []
  for (const [key, items] of buckets) {
    const ordered = sortActivities(items, 'urgent')
    const live = ordered.filter((item) => item.status !== 'ended')
    const current = live[0] ?? ordered[ordered.length - 1]
    if (!current) continue
    const ended = ordered.filter((item) => item.status === 'ended' && item.id !== current.id)
    groups.push({
      key,
      stage: current.challenge_stage ?? '',
      game: current.game,
      current,
      next: live[0] ? live[1] ?? null : null,
      previous: ended.length ? ended[ended.length - 1] : null,
    })
  }

  return groups.sort((left, right) => {
    if (mode === 'latestStart') {
      return (
        compare(startValue(right.current), startValue(left.current))
        || compare(endValue(left.current), endValue(right.current))
      )
    }
    return (
      compare(endValue(left.current), endValue(right.current))
      || compare(startValue(left.current), startValue(right.current))
    )
  })
}

export function challengePeriodName(activity: Activity): string {
  const stage = activity.challenge_stage
  if (!stage) return activity.name
  const stripped = activity.name
    .replace(stage, '')
    .replace(/^[\s•·\-–—:：]+/, '')
    .trim()
  return stripped || activity.name
}

export function challengeTone(group: ChallengeGroup): string {
  if (group.current.completed) return 'done'
  return countdownInfo(group.current.start_at, group.current.end_at).tone
}
