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

type ChallengeWindow = 'ended' | 'current' | 'upcoming'

function challengeWindow(activity: Activity, now: number): ChallengeWindow {
  const start = startValue(activity)
  const end = endValue(activity)
  if (Number.isFinite(end) && now >= end) return 'ended'
  if (Number.isFinite(start) && now < start) return 'upcoming'
  if (Number.isFinite(start) || Number.isFinite(end)) return 'current'
  if (activity.status === 'ended') return 'ended'
  if (activity.status === 'upcoming') return 'upcoming'
  return 'current'
}

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
  now = Date.now(),
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
    const currentPeriods = items
      .filter((item) => challengeWindow(item, now) === 'current')
      .sort((left, right) => (
        compare(endValue(left), endValue(right))
        || compare(startValue(left), startValue(right))
      ))
    const upcomingPeriods = items
      .filter((item) => challengeWindow(item, now) === 'upcoming')
      .sort((left, right) => (
        compare(startValue(left), startValue(right))
        || compare(endValue(left), endValue(right))
      ))
    const endedPeriods = items
      .filter((item) => challengeWindow(item, now) === 'ended')
      .sort((left, right) => (
        compare(endValue(right), endValue(left))
        || compare(startValue(right), startValue(left))
      ))
    const current = currentPeriods[0] ?? upcomingPeriods[0] ?? endedPeriods[0]
    if (!current) continue
    const previous = endedPeriods.find((item) => item.id !== current.id) ?? null
    const next = currentPeriods.length > 0
      ? upcomingPeriods[0] ?? currentPeriods[1] ?? null
      : upcomingPeriods[1] ?? null
    groups.push({
      key,
      stage: current.challenge_stage ?? '',
      game: current.game,
      current,
      next,
      previous,
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
