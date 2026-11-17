import { useEffect, useState } from 'react'
import type { Game, User } from '../types'

const STORAGE_KEY = 'activity.games'
const CHANGE_EVENT = 'activity:games-changed'

function sanitize(value: unknown): string[] | null {
  if (!Array.isArray(value)) return null
  const keys = value.filter(
    (item): item is string => typeof item === 'string' && item.trim().length > 0,
  )
  return [...new Set(keys.map((item) => item.trim().toLowerCase()))]
}

export function readLocalGames(): string[] | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    return raw === null ? null : sanitize(JSON.parse(raw))
  } catch {
    return null
  }
}

export function writeLocalGames(keys: string[]) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify([...new Set(keys)]))
  } catch {
    // The current selection still applies for this session when storage is unavailable.
  }
  window.dispatchEvent(new Event(CHANGE_EVENT))
}

export function useLocalGames(): string[] | null {
  const [keys, setKeys] = useState<string[] | null>(readLocalGames)

  useEffect(() => {
    const sync = () => setKeys(readLocalGames())
    window.addEventListener('storage', sync)
    window.addEventListener(CHANGE_EVENT, sync)
    return () => {
      window.removeEventListener('storage', sync)
      window.removeEventListener(CHANGE_EVENT, sync)
    }
  }, [])

  return keys
}

export function selectedGamesFor(
  games: Game[],
  user: User | null,
  localKeys: string[] | null,
): Game[] {
  if (user) return games.filter((game) => game.selected)
  const known = new Set(games.map((game) => game.key))
  const keys = new Set((localKeys ?? [...known]).filter((key) => known.has(key)))
  return games.filter((game) => keys.has(game.key))
}
