import type { Activity, AuthResponse, Banner, Code, Game, SyncResult, User } from './types'

const TOKEN_KEY = 'activity.token'

export function getToken(): string | null {
  return window.localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (token) {
    window.localStorage.setItem(TOKEN_KEY, token)
  } else {
    window.localStorage.removeItem(TOKEN_KEY)
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getToken()
  const headers = new Headers(init?.headers)
  if (init?.body) {
    headers.set('Content-Type', 'application/json')
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(`/api${path}`, { ...init, headers })
  if (!response.ok) {
    let message = `请求失败（HTTP ${response.status}）`
    try {
      const body = await response.json()
      message = body.message || body.error || message
    } catch {
      // keep status message
    }
    if (response.status === 401 && getToken()) {
      setToken(null)
      window.dispatchEvent(new Event('auth:expired'))
    }
    throw new Error(message)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export const api = {
  publicKey: () => request<{ algorithm: string; public_key: string }>('/auth/public-key'),
  register: (payload: { username: string; password_ciphertext: string }) =>
    request<AuthResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  login: (payload: { username: string; password_ciphertext: string }) =>
    request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  changePassword: (payload: {
    current_password_ciphertext: string
    new_password_ciphertext: string
  }) =>
    request<AuthResponse>('/auth/password', {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
  createRecoveryCode: (password_ciphertext: string) =>
    request<{ recovery_code: string }>('/auth/recovery-code', {
      method: 'POST',
      body: JSON.stringify({ password_ciphertext }),
    }),
  resetPassword: (payload: {
    username: string
    recovery_code: string
    new_password_ciphertext: string
  }) =>
    request<void>('/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  deleteAccount: (password_ciphertext: string) =>
    request<void>('/auth/account', {
      method: 'DELETE',
      body: JSON.stringify({ password_ciphertext }),
    }),
  me: () => request<User>('/auth/me'),

  games: () => request<Game[]>('/games'),
  updateGames: (games: string[]) =>
    request<Game[]>('/games', { method: 'PUT', body: JSON.stringify({ games }) }),
  sync: () => request<SyncResult>('/sync', { method: 'POST' }),

  activities: (game?: string) =>
    request<Activity[]>(`/activities${game ? `?game=${encodeURIComponent(game)}` : ''}`),
  banners: (game?: string) =>
    request<Banner[]>(`/banners${game ? `?game=${encodeURIComponent(game)}` : ''}`),
  codes: (game?: string, active = true) => {
    const params = new URLSearchParams()
    if (game) params.set('game', game)
    if (!active) params.set('active', 'false')
    const query = params.toString()
    return request<Code[]>(`/codes${query ? `?${query}` : ''}`)
  },
  setCompleted: (id: string, completed: boolean) =>
    request<Activity>(`/activities/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ completed }),
    }),
}
