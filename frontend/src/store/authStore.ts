import { create } from 'zustand'

type AuthState = { token: string | null; setToken: (t: string) => void }
export const useAuthStore = create<AuthState>((set) => ({ token: null, setToken: (token) => set({ token }) }))
