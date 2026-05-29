import { createTheme, type Theme } from '@mui/material'

export type ThemeMode = 'light' | 'dark'

// Shared shape/typography so light and dark stay visually consistent.
const base = {
  shape: { borderRadius: 14 },
  typography: {
    fontFamily: 'Inter, system-ui, sans-serif',
    h4: { fontSize: '1.75rem', fontWeight: 750 },
    h5: { fontSize: '1.3rem', fontWeight: 700 },
    h6: { fontWeight: 700 },
  },
} as const

const lightPalette = {
  mode: 'light' as const,
  primary: { main: '#2358ff' },
  secondary: { main: '#1f9f8a' },
  background: { default: '#eef3fb', paper: '#ffffff' },
  warning: { main: '#e8861f' },
  success: { main: '#1d9c5b' },
  error: { main: '#d23b3b' },
  divider: '#dce4f2',
}

// Clinical dark: deep slate-blue surfaces, brighter accents that pop on dark —
// the default mode radiologists expect (less eye strain than a white UI).
const darkPalette = {
  mode: 'dark' as const,
  primary: { main: '#5b8cff' },
  secondary: { main: '#2dd4bf' },
  background: { default: '#0b1220', paper: '#131c2e' },
  warning: { main: '#f5a623' },
  success: { main: '#34d399' },
  error: { main: '#f87171' },
  text: { primary: '#e6edf7', secondary: '#9fb0c8' },
  divider: 'rgba(255,255,255,0.10)',
}

export function createAppTheme(mode: ThemeMode): Theme {
  const isDark = mode === 'dark'
  return createTheme({
    ...base,
    palette: isDark ? darkPalette : lightPalette,
    components: {
      MuiCard: {
        styleOverrides: {
          root: {
            border: `1px solid ${isDark ? 'rgba(255,255,255,0.08)' : '#e6ecf7'}`,
            backgroundImage: 'none',
          },
        },
      },
      MuiAppBar: {
        styleOverrides: {
          root: {
            backgroundColor: isDark ? '#0e1726' : '#ffffff',
            color: isDark ? '#e6edf7' : 'inherit',
          },
        },
      },
    },
  })
}

// Surface colors the imaging viewers (2D canvas / 3D scene) read for their
// own backgrounds so they blend with the active theme instead of fighting it.
export function viewerSurface(mode: ThemeMode) {
  return mode === 'dark'
    ? { canvasBg: '#05080f', sceneBg: '#0b1220', panelBg: '#131c2e' }
    : { canvasBg: '#0b0f1a', sceneBg: '#f1f5fc', panelBg: '#ffffff' }
}
