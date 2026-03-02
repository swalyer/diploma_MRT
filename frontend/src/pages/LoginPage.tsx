import { Alert, Box, Button, Card, CardContent, Stack, TextField, Typography } from '@mui/material'
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useAuthStore } from '../store/authStore'

export function LoginPage() {
  const [email, setEmail] = useState('admin@demo.local')
  const [password, setPassword] = useState('Admin123!')
  const [error, setError] = useState<string | null>(null)
  const setToken = useAuthStore((s) => s.setToken)
  const navigate = useNavigate()
  const location = useLocation()

  const login = async () => {
    setError(null)
    try {
      const response = await api.post('/auth/login', { email, password })
      setToken(response.data.token)
      navigate((location.state as { from?: string } | undefined)?.from ?? '/cases')
    } catch {
      setError('Authentication failed. Check credentials and backend availability.')
    }
  }

  return <Box sx={{ minHeight: '70vh', display: 'grid', placeItems: 'center' }}>
    <Card sx={{ maxWidth: 460, width: '100%' }}>
      <CardContent>
        <Stack spacing={2.5}>
          <Typography variant="h4" fontWeight={700}>Welcome back</Typography>
          <Typography color="text.secondary">Decision-support only. Clinical interpretation remains physician responsibility.</Typography>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label="Email" value={email} onChange={(e)=>setEmail(e.target.value)} fullWidth />
          <TextField type="password" label="Password" value={password} onChange={(e)=>setPassword(e.target.value)} fullWidth />
          <Button variant="contained" size="large" onClick={login}>Sign in</Button>
        </Stack>
      </CardContent>
    </Card>
  </Box>
}
