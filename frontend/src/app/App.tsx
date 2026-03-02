import React, { useEffect } from 'react'
import {
  AppBar,
  Avatar,
  Box,
  Breadcrumbs,
  Button,
  Chip,
  Container,
  CssBaseline,
  IconButton,
  Link,
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
    background: { default: '#f2f5fb', paper: '#ffffff' },
    warning: { main: '#e8861f' },
    success: { main: '#1d9c5b' }
  },
  shape: { borderRadius: 14 },
  spacing: 8,
  typography: {
    fontFamily: 'Inter, system-ui, sans-serif',
    h4: { fontSize: '1.75rem', fontWeight: 700 },
    h5: { fontSize: '1.35rem', fontWeight: 700 }
  },
  components: {
    MuiCard: { styleOverrides: { root: { border: '1px solid #e3e9f4', boxShadow: '0 10px 24px rgba(22,34,66,.04)' } } },
    MuiButton: { styleOverrides: { root: { borderRadius: 12, textTransform: 'none', fontWeight: 600 } } },
    MuiChip: { styleOverrides: { root: { borderRadius: 10, fontWeight: 600 } } }
  }
})

function ProtectedRoute({ children }: { children: React.ReactElement }) {
  const token = useAuthStore((s) => s.token)
  const location = useLocation()
  if (!token) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}

function TopNav() {
  const navigate = useNavigate()
  const location = useLocation()
  const token = useAuthStore((s) => s.token)
  const clearToken = useAuthStore((s) => s.clearToken)

  const crumbs = location.pathname.split('/').filter(Boolean)

  return (
    <AppBar position="sticky" color="inherit" elevation={0} sx={{ borderBottom: '1px solid #e3e9f4', backdropFilter: 'blur(8px)' }}>
      <Toolbar sx={{ minHeight: 76 }}>
        <Stack direction="row" alignItems="center" spacing={2} sx={{ flexGrow: 1 }}>
          <IconButton onClick={() => navigate('/cases')}><Avatar sx={{ width: 34, height: 34, bgcolor: 'primary.main' }}>LI</Avatar></IconButton>
          <Stack spacing={0.25}>
            <Typography variant="h6" fontWeight={800}>Liver Insight Console</Typography>
            <Typography variant="caption" color="text.secondary">CT/MRI decision-support platform · MVP</Typography>
          </Stack>
          {token && <Chip size="small" color="primary" label="Authenticated session" />}
        </Stack>
        {token && (
          <Stack direction="row" spacing={1}>
            <Button onClick={() => navigate('/cases')}>Cases</Button>
            <Button onClick={() => navigate('/cases/new')}>Intake</Button>
            <Button onClick={() => navigate('/admin')}>Admin</Button>
            <Button variant="outlined" onClick={() => { clearToken(); navigate('/login') }}>Logout</Button>
          </Stack>
        )}
      </Toolbar>
      {token && (
        <Container maxWidth="xl" sx={{ pb: 1 }}>
          <Breadcrumbs separator="›" aria-label="breadcrumb">
            <Link underline="hover" color="inherit" onClick={() => navigate('/cases')} sx={{ cursor: 'pointer' }}>Home</Link>
            {crumbs.map((c, idx) => <Typography key={`${c}-${idx}`} color="text.secondary">{c}</Typography>)}
          </Breadcrumbs>
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
          <Route path="/admin" element={<ProtectedRoute><AdminPage /></ProtectedRoute>} />
          <Route path="*" element={<Navigate to="/cases" replace />} />
        </Routes>
      </Container>
    </ThemeProvider>
  )
}
