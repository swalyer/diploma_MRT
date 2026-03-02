import { create } from 'zustand'

const TOKEN_KEY = 'mrt.auth.token'

type AuthState = {
  token: string | null
  setToken: (t: string) => void
  clearToken: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  token: typeof window !== 'undefined' ? localStorage.getItem(TOKEN_KEY) : null,
  setToken: (token) => {
    localStorage.setItem(TOKEN_KEY, token)
    set({ token })
  },
  clearToken: () => {
    localStorage.removeItem(TOKEN_KEY)
    set({ token: null })
  }
}))
