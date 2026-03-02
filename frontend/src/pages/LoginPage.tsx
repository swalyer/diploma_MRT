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
      setError('Authentication failed. Check credentials, token expiry, or backend availability.')
    }
  }

  return (
    <Grid2 container spacing={2}>
      <Grid2 size={{ xs: 12, lg: 7 }}>
        <Card sx={{ height: '100%', minHeight: 520, background: 'linear-gradient(145deg, #102a66, #1d4bc2)', color: '#fff' }}>
          <CardContent sx={{ p: 4 }}>
            <Stack spacing={2}>
              <Chip label="Liver CT/MRI Analysis" sx={{ bgcolor: 'rgba(255,255,255,.16)', color: '#fff', width: 'fit-content' }} />
              <Typography variant="h4" fontWeight={800}>Clinical-grade imaging workspace for decision support</Typography>
              <Typography sx={{ opacity: 0.9 }}>Review segmentation artifacts, verify 2D and 3D outputs, and communicate model limitations explicitly.</Typography>
              <Divider sx={{ borderColor: 'rgba(255,255,255,.2)', my: 1 }} />
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label="CT real pipeline" sx={{ bgcolor: '#fff', color: '#102a66' }} />
                <Chip label="MRI experimental" sx={{ bgcolor: '#ffdca8', color: '#5a3400' }} />
                <Chip label="Mock / Real traceability" sx={{ bgcolor: '#c3ffea', color: '#004e3e' }} />
              </Stack>
              <Typography variant="body2" sx={{ opacity: .85, mt: 2 }}>
                ⚠️ Decision-support only. Outputs must be interpreted and validated by a qualified physician.
              </Typography>
            </Stack>
          </CardContent>
        </Card>
      </Grid2>
      <Grid2 size={{ xs: 12, lg: 5 }}>
        <Card sx={{ minHeight: 520, display: 'grid', alignItems: 'center' }}>
          <CardContent sx={{ p: 4 }}>
            <Stack spacing={2.5}>
              <Typography variant="h5">Sign in</Typography>
              <Typography color="text.secondary">Use your secured account to access case operations and protected artifacts.</Typography>
              {error && <Alert severity="error">{error}</Alert>}
              <TextField label="Email" value={email} onChange={(e)=>setEmail(e.target.value)} fullWidth />
              <TextField type="password" label="Password" value={password} onChange={(e)=>setPassword(e.target.value)} fullWidth />
              <Box><Button variant="contained" size="large" onClick={login}>Sign in to console</Button></Box>
            </Stack>
          </CardContent>
        </Card>
      </Grid2>
    </Grid2>
  )
}
