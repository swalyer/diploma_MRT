import { Alert, Box, Button, Card, CardContent, Chip, Divider, Grid2, Stack, TextField, Typography } from '@mui/material'
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

  return (
    <Grid2 container spacing={2.5}>
      <Grid2 size={{ xs: 12, lg: 7 }}>
        <Card sx={{ height: '100%', minHeight: 540, background: 'linear-gradient(145deg, #0c2a73, #1b58d8)', color: '#fff' }}>
          <CardContent sx={{ p: 4 }}>
            <Stack spacing={2}>
              <Chip label="Liver CT/MRI Decision Support" sx={{ bgcolor: 'rgba(255,255,255,.18)', color: '#fff', width: 'fit-content' }} />
              <Typography variant="h4" fontWeight={800}>Production-style imaging operations workspace</Typography>
              <Typography sx={{ opacity: 0.95 }}>Inspect artifacts, verify model outputs, and keep implementation truth explicit across 2D/3D/reporting surfaces.</Typography>
              <Divider sx={{ borderColor: 'rgba(255,255,255,.25)', my: 1 }} />
              <Stack spacing={0.5}>
                <Typography>• Authenticated artifact access</Typography>
                <Typography>• NIfTI 2D rendering with overlays</Typography>
                <Typography>• Artifact-backed liver/lesion 3D meshes</Typography>
                <Typography>• Explicit inferred vs verified labels</Typography>
              </Stack>
              <Box sx={{ mt: 2, p: 1.5, borderRadius: 2, bgcolor: 'rgba(255,255,255,.14)' }}>
                <Typography variant="body2">⚠️ Clinical decision-support only. Outputs are not standalone diagnosis.</Typography>
              </Box>
            </Stack>
          </CardContent>
        </Card>
      </Grid2>
      <Grid2 size={{ xs: 12, lg: 5 }}>
        <Card sx={{ minHeight: 540, display: 'grid', alignItems: 'center' }}>
          <CardContent sx={{ p: 4 }}>
            <Stack spacing={2.5}>
              <Typography variant="h5">Sign in to workspace</Typography>
              <Typography color="text.secondary">Use your secured account to access protected case data and artifact viewers.</Typography>
              {error && <Alert severity="error">{error}</Alert>}
              <TextField label="Email" value={email} onChange={(e) => setEmail(e.target.value)} fullWidth inputProps={{ 'data-testid': 'login-email' }} />
              <TextField type="password" label="Password" value={password} onChange={(e) => setPassword(e.target.value)} fullWidth inputProps={{ 'data-testid': 'login-password' }} />
              <Button variant="contained" size="large" onClick={login} data-testid="login-submit">Sign in</Button>
            </Stack>
          </CardContent>
        </Card>
      </Grid2>
    </Grid2>
  )
}
