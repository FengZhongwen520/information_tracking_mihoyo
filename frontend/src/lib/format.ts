import type { ActivityReward } from '../types'

const DATE_TIME = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

const DATE_FULL = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return DATE_TIME.format(date).replace(/\//g, '-')
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return DATE_FULL.format(date).replace(/\//g, '-')
}

export function formatRelative(value: string | null | undefined): string {
  if (!value) return '尚未同步'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const seconds = Math.round((date.getTime() - Date.now()) / 1000)
  const abs = Math.abs(seconds)
  const suffix = seconds >= 0 ? '后' : '前'
  if (abs < 60) return '刚刚'
  if (abs < 3600) return `${Math.round(abs / 60)} 分钟${suffix}`
  if (abs < 86400) return `${Math.round(abs / 3600)} 小时${suffix}`
  return `${Math.round(abs / 86400)} 天${suffix}`
}

export function formatDuration(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000))
  const days = Math.floor(total / 86400)
  const hours = Math.floor((total % 86400) / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  const pad = (value: number) => String(value).padStart(2, '0')
  if (days > 0) return `${days} 天 ${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
}

const RANGE_FORMAT = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

export function formatRange(startAt: string | null, endAt: string | null): string {
  const one = (value: string | null) => {
    if (!value) return '—'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return RANGE_FORMAT.format(date).replace(/\//g, '/')
  }
  return `${one(startAt)} ~ ${one(endAt)}`
}

export function formatTimestamp(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return RANGE_FORMAT.format(date)
}

export function remainingFraction(startAt: string | null, endAt: string | null): number {
  if (!endAt) return 0
  const end = new Date(endAt).getTime()
  const now = Date.now()
  if (Number.isNaN(end) || now >= end) return 0
  if (!startAt) return 1
  const start = new Date(startAt).getTime()
  const total = end - start
  if (Number.isNaN(start) || total <= 0) return 1
  return Math.min(1, Math.max(0, (end - now) / total))
}

export type CountdownTone = 'critical' | 'soon' | 'far' | 'upcoming' | 'ended' | 'unknown'

export interface CountdownInfo {
  text: string
  tone: CountdownTone
}

export function countdownInfo(startAt: string | null, endAt: string | null): CountdownInfo {
  if (!endAt) return { text: '时间待定', tone: 'unknown' }
  const end = new Date(endAt).getTime()
  const now = Date.now()
  if (Number.isNaN(end)) return { text: '时间待定', tone: 'unknown' }
  if (now >= end) return { text: '已结束', tone: 'ended' }

  const start = startAt ? new Date(startAt).getTime() : null
  if (start !== null && !Number.isNaN(start) && now < start) {
    const left = start - now
    const days = Math.floor(left / 86_400_000)
    const hours = Math.floor((left % 86_400_000) / 3_600_000)
    return {
      text: days >= 1 ? `${days} 天` : `${hours} 小时`,
      tone: 'upcoming',
    }
  }

  const left = end - now
  const days = Math.floor(left / 86_400_000)
  const hours = Math.floor((left % 86_400_000) / 3_600_000)
  const minutes = Math.floor((left % 3_600_000) / 60_000)
  const text =
    days >= 10 ? `约 ${days} 天` : days >= 1 ? `${days}天${hours}小时` : `${hours}小时${minutes}分`
  const tone: CountdownTone = left <= 86_400_000 ? 'critical' : left <= 7 * 86_400_000 ? 'soon' : 'far'
  return { text, tone }
}

const REWARD_NAMES =
  '原石|星琼|星轨专票|星轨通票|邦布券|加密母带|菲林|摩拉|纠缠之缘|相遇之缘|智识之冕'

export function extractRewards(description: string | null): string | null {
  if (!description) return null
  const cleaned = description.replace(/[[\]【】]/g, '')
  const pattern = new RegExp(`(${REWARD_NAMES})\\s*[×xX*＊]\\s*(\\d+)`, 'g')
  const found: string[] = []
  for (const match of cleaned.matchAll(pattern)) {
    const item = `${match[1]} ×${match[2]}`
    if (!found.includes(item)) found.push(item)
  }
  return found.length ? found.slice(0, 3).join(' · ') : null
}

export function formatRewards(rewards: ActivityReward[] | null | undefined): string | null {
  if (!rewards || rewards.length === 0) return null
  const items = rewards
    .filter((reward) => reward.name?.trim())
    .slice(0, 3)
    .map((reward) =>
      reward.amount && reward.amount > 0
        ? `${reward.name.trim()} ×${reward.amount}`
        : reward.name.trim(),
    )
  return items.length ? items.join(' · ') : null
}

export const STATUS_LABEL: Record<string, string> = {
  active: '进行中',
  ending: '即将结束',
  upcoming: '未开始',
  ended: '已结束',
  unknown: '时间待定',
}

export const GAME_NAMES: Record<string, string> = {
  sr: '崩坏：星穹铁道',
  ys: '原神',
  zzz: '绝区零',
}

export const GAME_SHORT: Record<string, string> = {
  sr: '星铁',
  ys: '原神',
  zzz: '绝区零',
}

const CODE_REWARD_NAMES: Record<string, string> = {
  'Stellar Jade': '星琼',
  Primogem: '原石',
  Polychrome: '菲林',
  Credit: '信用点',
  Mora: '摩拉',
  Denny: '丁尼',
  "Traveler's Guide": '漫游指南',
  "Adventurer's Experience": '冒险家的经验',
  "Hero's Wit": '大英雄的经验',
  Fuel: '燃料',
  'Free items': '随机道具',
}

export function formatReward(value: string): string {
  const text = value.trim()
  const direct = CODE_REWARD_NAMES[text]
  if (direct) return direct
  const match = text.match(/^(.+?)\s*[x×]\s*([\d,]+)$/i)
  if (!match) return text
  const name = CODE_REWARD_NAMES[match[1].trim()] ?? match[1].trim()
  return `${name} ×${match[2]}`
}
