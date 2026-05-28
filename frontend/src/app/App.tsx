import React, { useEffect } from 'react'
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
  Stack,
  ThemeProvider,
  Toolbar,
  Typography,
  createTheme
} from '@mui/material'
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { LoginPage } from '../pages/LoginPage'
import { CasesPage } from '../pages/CasesPage'
import { CreateCasePage } from '../pages/CreateCasePage'
import { CaseDetailsPage } from '../pages/CaseDetailsPage'
import { AdminPage } from '../pages/AdminPage'
import { useAuthStore } from '../store/authStore'

const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#2358ff' },
    secondary: { main: '#1f9f8a' },
    background: { default: '#eef3fb', paper: '#ffffff' },
    warning: { main: '#e8861f' },
    success: { main: '#1d9c5b' }
  },
  shape: { borderRadius: 14 },
  typography: {
    fontFamily: 'Inter, system-ui, sans-serif',
    h4: { fontSize: '1.75rem', fontWeight: 750 },
    h5: { fontSize: '1.3rem', fontWeight: 700 }
  }
})

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
    <AppBar position="sticky" color="inherit" elevation={0} sx={{ borderBottom: '1px solid #dce4f2' }}>
      <Toolbar sx={{ minHeight: 78 }}>
        <Stack direction="row" alignItems="center" spacing={1.5} sx={{ flexGrow: 1 }}>
          <Avatar sx={{ width: 36, height: 36, bgcolor: 'primary.main', fontWeight: 800, cursor: 'pointer' }} onClick={() => navigate('/cases')}>LI</Avatar>
          <Stack spacing={0.1}>
            <Typography variant="h6" fontWeight={800}>Liver Insight Console</Typography>
            <Typography variant="caption" color="text.secondary">Decision-support workspace</Typography>
          </Stack>
        </Stack>

        {token && (
          <Stack direction="row" spacing={1} alignItems="center">
            {navItems.map((item) => (
              <Button
                key={item.path}
                variant={location.pathname.startsWith(item.path) ? 'contained' : 'text'}
                onClick={() => navigate(item.path)}
              >
                {item.label}
              </Button>
            ))}
            <Chip size="small" color="primary" label={role === 'ROLE_ADMIN' ? 'Admin' : 'Doctor'} />
            <Button variant="outlined" onClick={() => { clearToken(); navigate('/login') }}>Logout</Button>
          </Stack>
        )}
      </Toolbar>
      {token && (
        <Container maxWidth="xl" sx={{ pb: 1.2 }}>
          <Card sx={{ px: 1.5, py: 0.8, bgcolor: '#f8fbff' }}>
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
