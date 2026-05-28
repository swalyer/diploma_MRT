import { create } from 'zustand'

const TOKEN_KEY = 'mrt.auth.token'

type UserRole = 'ROLE_ADMIN' | 'ROLE_DOCTOR' | null

function decodeBase64Url(value: string): string | null {
  try {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    return atob(padded)
  } catch {
    return null
  }
}

function resolveRole(token: string | null): UserRole {
  if (!token) return null
  const parts = token.split('.')
  if (parts.length < 2) return null
  const payloadText = decodeBase64Url(parts[1])
  if (!payloadText) return null
  try {
    const payload = JSON.parse(payloadText) as { role?: string }
    if (payload.role === 'ADMIN') return 'ROLE_ADMIN'
    if (payload.role === 'DOCTOR') return 'ROLE_DOCTOR'
    return null
  } catch {
    return null
  }
}

type AuthState = {
  token: string | null
  role: UserRole
  setToken: (t: string) => void
  clearToken: () => void
}

const initialToken = typeof window !== 'undefined' ? localStorage.getItem(TOKEN_KEY) : null

export const useAuthStore = create<AuthState>((set) => ({
  token: initialToken,
  role: resolveRole(initialToken),
  setToken: (token) => {
    localStorage.setItem(TOKEN_KEY, token)
    set({ token, role: resolveRole(token) })
  },
  clearToken: () => {
    localStorage.removeItem(TOKEN_KEY)
    set({ token: null, role: null })
  }
}))
