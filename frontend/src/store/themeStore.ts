import { create } from 'zustand'
import type { ThemeMode } from '../app/theme'

const MODE_KEY = 'mrt.theme.mode'

function initialMode(): ThemeMode {
  if (typeof window === 'undefined') return 'dark'
  const stored = localStorage.getItem(MODE_KEY)
  if (stored === 'light' || stored === 'dark') return stored
  // Default to the OS preference; clinical UIs lean dark, so dark is the fallback.
  const prefersLight = window.matchMedia?.('(prefers-color-scheme: light)').matches
  return prefersLight ? 'light' : 'dark'
}

type ThemeState = {
  mode: ThemeMode
  toggleMode: () => void
  setMode: (mode: ThemeMode) => void
}

export const useThemeStore = create<ThemeState>((set) => ({
  mode: initialMode(),
  toggleMode: () =>
    set((state) => {
      const mode: ThemeMode = state.mode === 'dark' ? 'light' : 'dark'
      if (typeof window !== 'undefined') localStorage.setItem(MODE_KEY, mode)
      return { mode }
    }),
  setMode: (mode) => {
    if (typeof window !== 'undefined') localStorage.setItem(MODE_KEY, mode)
    set({ mode })
  },
}))
