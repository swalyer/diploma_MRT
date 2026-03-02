import React from 'react'
import { AppBar, Box, Button, Chip, Container, CssBaseline, Stack, ThemeProvider, Toolbar, Typography, createTheme } from '@mui/material'
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
    primary: { main: '#2459ff' },
    secondary: { main: '#2b7a78' },
    background: { default: '#f4f7fc', paper: '#ffffff' }
  },
  shape: { borderRadius: 12 },
  typography: { fontFamily: 'Inter, system-ui, sans-serif' }
})

function ProtectedRoute({ children }: { children: React.ReactElement }) {
  const token = useAuthStore((s) => s.token)
  const location = useLocation()
  if (!token) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}

function TopNav() {
  const navigate = useNavigate()
  const token = useAuthStore((s) => s.token)
  const clearToken = useAuthStore((s) => s.clearToken)
  return (
    <AppBar position="sticky" color="inherit" elevation={0} sx={{ borderBottom: '1px solid #e5eaf2' }}>
      <Toolbar>
        <Stack direction="row" alignItems="center" spacing={1.5} sx={{ flexGrow: 1 }}>
          <Typography variant="h6" fontWeight={700}>Liver Insight</Typography>
          <Chip size="small" label="MVP" color="primary" />
          {token && (
            <>
              <Button onClick={() => navigate('/cases')}>Cases</Button>
              <Button onClick={() => navigate('/cases/new')}>Create case</Button>
              <Button onClick={() => navigate('/admin')}>Admin</Button>
            </>
          )}
        </Stack>
        {token && <Button onClick={() => { clearToken(); navigate('/login') }}>Logout</Button>}
      </Toolbar>
    </AppBar>
  )
}

export function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <TopNav />
      <Container maxWidth="xl" sx={{ py: 3 }}>
        <Box>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/cases" element={<ProtectedRoute><CasesPage /></ProtectedRoute>} />
            <Route path="/cases/new" element={<ProtectedRoute><CreateCasePage /></ProtectedRoute>} />
            <Route path="/cases/:id" element={<ProtectedRoute><CaseDetailsPage /></ProtectedRoute>} />
            <Route path="/admin" element={<ProtectedRoute><AdminPage /></ProtectedRoute>} />
            <Route path="*" element={<Navigate to="/cases" replace />} />
          </Routes>
        </Box>
      </Container>
    </ThemeProvider>
  )
}
