import { describe, expect, it } from 'vitest'
import type { Activity } from '../types'
import { buildChallengeGroups, challengeKey } from './board'

function activity(
  id: string,
  name: string,
  startAt: string,
  endAt: string,
  status: Activity['status'],
  completed = false,
): Activity {
  return {
    id,
    game: 'sr',
    game_label: '崩坏：星穹铁道',
    name,
    kind: '虚构叙事',
    description: null,
    start_at: startAt,
    end_at: endAt,
    cover: null,
    rewards: [],
    version: '4.7',
    version_name: null,
    challenge_stage: '虚构叙事',
    status,
    completed,
    completed_at: completed ? startAt : null,
  }
}

function groupAt(items: Activity[], now: string) {
  const keys = new Set(items.map(challengeKey).filter((key): key is string => key !== null))
  return buildChallengeGroups(items, keys, 'urgent', Date.parse(now))[0]
}

describe('buildChallengeGroups', () => {
  const current = activity(
    'current',
    '虚构叙事•取象为文',
    '2026-10-19T11:00:00+08:00',
    '2026-11-30T11:00:00+08:00',
    'active',
    true,
  )
  const next = activity(
    'next',
    '虚构叙事•虚事成理',
    '2026-11-30T11:00:00+08:00',
    '2027-01-11T11:00:00+08:00',
    'upcoming',
  )

  it('keeps a completed current period visible while the next one is upcoming', () => {
    const group = groupAt([current, next], '2026-11-25T12:00:00+08:00')

    expect(group.current).toBe(current)
    expect(group.current.completed).toBe(true)
    expect(group.next).toBe(next)
  })

  it('switches to the next period at its exact start time', () => {
    const group = groupAt([current, next], '2026-11-30T11:00:00+08:00')

    expect(group.current).toBe(next)
    expect(group.previous).toBe(current)
    expect(group.current.completed).toBe(false)
  })

  it('uses period timestamps instead of stale API status', () => {
    const staleCurrent = { ...current, status: 'active' as const }
    const staleNext = { ...next, status: 'upcoming' as const }
    const group = groupAt([staleCurrent, staleNext], '2026-12-01T00:00:00+08:00')

    expect(group.current).toBe(staleNext)
    expect(group.previous).toBe(staleCurrent)
  })
})
