export interface User {
  id: string
  username: string
  games: string[]
  created_at: string
}

export interface AuthResponse {
  token: string
  user: User
  recovery_code: string | null
}

export interface SyncStatus {
  status: 'ok' | 'error' | 'never'
  last_attempt_at: string | null
  last_success_at: string | null
  last_error: string | null
  activity_count: number
  version: string | null
}

export interface Game {
  key: string
  label: string
  short_label: string
  selected: boolean
  activity_count: number
  sync: SyncStatus
}

export type ActivityStatus = 'active' | 'ending' | 'upcoming' | 'ended' | 'unknown'

export interface ActivityReward {
  name: string
  amount: number | null
  icon: string | null
  rarity: string | null
}

export interface Activity {
  id: string
  game: string
  game_label: string
  name: string
  kind: string | null
  description: string | null
  start_at: string | null
  end_at: string | null
  cover: string | null
  rewards: ActivityReward[]
  version: string | null
  version_name: string | null
  challenge_stage: string | null
  status: ActivityStatus
  completed: boolean
  completed_at: string | null
}

export interface BannerItem {
  name: string
  icon: string | null
  rarity: string | null
  element: string | null
  path: string | null
  profession: string | null
}

export interface Banner {
  id: string
  game: string
  game_label: string
  name: string
  version: string | null
  banner_type: string
  start_at: string | null
  end_at: string | null
  items: BannerItem[]
  status: ActivityStatus
}

export interface Code {
  id: string
  game: string
  game_label: string
  code: string
  rewards: string[]
}

export interface SyncResult {
  started: boolean
  games: Game[]
  message: string | null
}
