import React, { useEffect, useMemo } from 'react'
import {
  AppBar,
  Avatar,
  Box,
  Breadcrumbs,
  Button,
  Card,
  Chip,
  Container,
  CssBaseline,
  IconButton,
  Stack,
  ThemeProvider,
  Toolbar,
  Tooltip,
  Typography
} from '@mui/material'
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { LoginPage } from '../pages/LoginPage'
import { CasesPage } from '../pages/CasesPage'
import { CreateCasePage } from '../pages/CreateCasePage'
import { CaseDetailsPage } from '../pages/CaseDetailsPage'
import { AdminPage } from '../pages/AdminPage'
import { useAuthStore } from '../store/authStore'
import { useThemeStore } from '../store/themeStore'
import { createAppTheme } from './theme'

function ThemeToggle() {
  const mode = useThemeStore((s) => s.mode)
  const toggleMode = useThemeStore((s) => s.toggleMode)
  const isDark = mode === 'dark'
  return (
    <Tooltip title={isDark ? 'Switch to light theme' : 'Switch to dark theme'}>
      <IconButton onClick={toggleMode} color="inherit" aria-label="toggle color theme" size="small">
        {isDark ? (
          // Sun
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
          </svg>
        ) : (
          // Moon
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
            <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
          </svg>
        )}
      </IconButton>
    </Tooltip>
  )
}

function ProtectedRoute({ children }: { children: React.ReactElement }) {
  const token = useAuthStore((s) => s.token)
  const location = useLocation()
  if (!token) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}

function AdminRoute({ children }: { children: React.ReactElement }) {
  const token = useAuthStore((s) => s.token)
  const role = useAuthStore((s) => s.role)
  const location = useLocation()
  if (!token) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (role !== 'ROLE_ADMIN') return <Navigate to="/cases" replace />
  return children
}

function TopNav() {
  const navigate = useNavigate()
  const location = useLocation()
  const token = useAuthStore((s) => s.token)
  const role = useAuthStore((s) => s.role)
  const clearToken = useAuthStore((s) => s.clearToken)

  const crumbs = location.pathname.split('/').filter(Boolean)
  const navItems = [
    { label: 'Cases', path: '/cases' },
    { label: 'Intake', path: '/cases/new' },
    ...(role === 'ROLE_ADMIN' ? [{ label: 'Admin', path: '/admin' }] : [])
  ]

  return (
    <AppBar position="sticky" color="inherit" elevation={0} sx={{ borderBottom: '1px solid', borderColor: 'divider' }}>
      <Toolbar sx={{ minHeight: 78 }}>
        <Stack direction="row" alignItems="center" spacing={1.5} sx={{ flexGrow: 1 }}>
          <Avatar sx={{ width: 36, height: 36, bgcolor: 'primary.main', fontWeight: 800, cursor: 'pointer' }} onClick={() => navigate('/cases')}>LI</Avatar>
          <Stack spacing={0.1}>
            <Typography variant="h6" fontWeight={800}>Liver Insight Console</Typography>
            <Typography variant="caption" color="text.secondary">Decision-support workspace</Typography>
          </Stack>
        </Stack>

        <Stack direction="row" spacing={1} alignItems="center">
          {token && navItems.map((item) => (
            <Button
              key={item.path}
              variant={location.pathname.startsWith(item.path) ? 'contained' : 'text'}
              onClick={() => navigate(item.path)}
            >
              {item.label}
            </Button>
          ))}
          {token && <Chip size="small" color="primary" label={role === 'ROLE_ADMIN' ? 'Admin' : 'Doctor'} />}
          <ThemeToggle />
          {token && <Button variant="outlined" onClick={() => { clearToken(); navigate('/login') }}>Logout</Button>}
        </Stack>
      </Toolbar>
      {token && (
        <Container maxWidth="xl" sx={{ pb: 1.2 }}>
          <Card sx={{ px: 1.5, py: 0.8, bgcolor: 'action.hover' }}>
            <Breadcrumbs separator="›" aria-label="breadcrumb">
              <Typography sx={{ cursor: 'pointer' }} onClick={() => navigate('/cases')}>home</Typography>
              {crumbs.map((c, idx) => <Typography key={`${c}-${idx}`} color="text.secondary">{c}</Typography>)}
            </Breadcrumbs>
          </Card>
        </Container>
      )}
    </AppBar>
  )
}

export function App() {
  const navigate = useNavigate()
  const mode = useThemeStore((s) => s.mode)
  const theme = useMemo(() => createAppTheme(mode), [mode])

  useEffect(() => {
    const onExpired = () => navigate('/login')
    window.addEventListener('auth:expired', onExpired)
    return () => window.removeEventListener('auth:expired', onExpired)
  }, [navigate])

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <TopNav />
      <Container maxWidth="xl" sx={{ py: 3 }}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/cases" element={<ProtectedRoute><CasesPage /></ProtectedRoute>} />
          <Route path="/cases/new" element={<ProtectedRoute><CreateCasePage /></ProtectedRoute>} />
          <Route path="/cases/:id" element={<ProtectedRoute><CaseDetailsPage /></ProtectedRoute>} />
          <Route path="/admin" element={<AdminRoute><AdminPage /></AdminRoute>} />
          <Route path="*" element={<Navigate to="/cases" replace />} />
        </Routes>
      </Container>
    </ThemeProvider>
  )
}
