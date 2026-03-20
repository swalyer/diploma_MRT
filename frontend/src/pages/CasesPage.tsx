import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  Divider,
  Grid2,
  MenuItem,
  Skeleton,
  Stack,
  TextField,
  Typography
} from '@mui/material'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { CaseItem } from '../types'

type CasesViewState = 'loading' | 'error' | 'success-empty' | 'success-results'

export function CasesPage() {
  const [items, setItems] = useState<CaseItem[]>([])
  const [requestState, setRequestState] = useState<'loading' | 'error' | 'success'>('loading')
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [modality, setModality] = useState('ALL')
  const [status, setStatus] = useState('ALL')

  const loadCases = async () => {
    setRequestState('loading')
    setError(null)
    try {
      const response = await api.get('/cases')
      setItems(response.data)
      setRequestState('success')
    } catch {
      setRequestState('error')
      setError('Cannot load cases. Session may be expired, access may be denied, or backend is unavailable.')
    }
  }

  useEffect(() => {
    loadCases().catch(() => setRequestState('error'))
  }, [])

  const filtered = useMemo(
    () =>
      items.filter(
        (c) =>
          (modality === 'ALL' || c.modality === modality) &&
          (status === 'ALL' || c.status === status) &&
          (query === '' || `${c.id}${c.patientPseudoId}`.toLowerCase().includes(query.toLowerCase()))
      ),
    [items, query, modality, status]
  )

  const viewState: CasesViewState =
    requestState === 'loading'
      ? 'loading'
      : requestState === 'error'
        ? 'error'
        : filtered.length === 0
          ? 'success-empty'
          : 'success-results'

  const completed = items.filter((c) => c.status === 'COMPLETED').length
  const processing = items.filter((c) => c.status === 'PROCESSING').length
  const failed = items.filter((c) => c.status === 'FAILED').length

  return (
    <Stack spacing={2.5}>
      <Card sx={{ p: 2.5 }}>
        <Stack direction={{ xs: 'column', lg: 'row' }} justifyContent="space-between" spacing={2}>
          <Box>
            <Typography variant="h4">Case Operations Console</Typography>
            <Typography color="text.secondary">Track intake, pipeline progress, and artifact readiness with implementation-honest status labeling.</Typography>
          </Box>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <Chip label={`${items.length} cases`} color="primary" />
            <Chip label={`${completed} completed`} color="success" />
            <Chip label={`${processing} processing`} color="warning" />
            <Chip label={`${failed} failed`} color="error" />
          </Stack>
        </Stack>
      </Card>

      <Grid2 container spacing={2}>
        <Grid2 size={{ xs: 12, md: 8 }}>
          <Card sx={{ p: 2 }}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <TextField label="Search case ID / pseudo ID" value={query} onChange={(e) => setQuery(e.target.value)} fullWidth />
              <TextField select label="Modality" value={modality} onChange={(e) => setModality(e.target.value)} sx={{ minWidth: 160 }}>
                <MenuItem value="ALL">All</MenuItem>
                <MenuItem value="CT">CT</MenuItem>
                <MenuItem value="MRI">MRI</MenuItem>
              </TextField>
              <TextField select label="Status" value={status} onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 180 }}>
                <MenuItem value="ALL">All</MenuItem>
                <MenuItem value="CREATED">CREATED</MenuItem>
                <MenuItem value="UPLOADED">UPLOADED</MenuItem>
                <MenuItem value="PROCESSING">PROCESSING</MenuItem>
                <MenuItem value="COMPLETED">COMPLETED</MenuItem>
                <MenuItem value="FAILED">FAILED</MenuItem>
              </TextField>
            </Stack>
          </Card>
        </Grid2>
        <Grid2 size={{ xs: 12, md: 4 }}>
          <Card sx={{ p: 2, height: '100%' }}>
            <Typography variant="subtitle1" fontWeight={700}>Truth labels</Typography>
            <Stack mt={1} spacing={1}>
              <Chip size="small" label="Authoritative API state" color="success" />
              <Chip size="small" label="Artifact availability" color="warning" />
              <Chip size="small" label="Unavailable / pending" />
            </Stack>
          </Card>
        </Grid2>
      </Grid2>

      {viewState === 'loading' && (
        <Grid2 container spacing={2}>
          {Array.from({ length: 6 }).map((_, i) => (
            <Grid2 key={i} size={{ xs: 12, md: 6, xl: 4 }}>
              <Card sx={{ p: 2 }}>
                <Skeleton height={30} />
                <Skeleton />
                <Skeleton width="60%" />
              </Card>
            </Grid2>
          ))}
        </Grid2>
      )}

      {viewState === 'error' && (
        <Card sx={{ p: 3 }}>
          <Stack spacing={2}>
            <Alert severity="error">{error}</Alert>
            <Box>
              <Button variant="contained" onClick={() => loadCases()}>Retry loading cases</Button>
            </Box>
          </Stack>
        </Card>
      )}

      {viewState === 'success-empty' && (
        <Card sx={{ p: 3 }}>
          <Typography variant="h6">No cases match your filters</Typography>
          <Typography color="text.secondary" mt={1}>This is a successful API response with zero matching rows (not a request failure).</Typography>
          <Divider sx={{ my: 2 }} />
          <Button component={Link} to="/cases/new" variant="contained">Create a new case</Button>
        </Card>
      )}

      {viewState === 'success-results' && (
        <Grid2 container spacing={2}>
          {filtered.map((c) => (
            <Grid2 size={{ xs: 12, md: 6, xl: 4 }} key={c.id}>
              <Card component={Link} to={`/cases/${c.id}`} sx={{ p: 2, textDecoration: 'none', display: 'block', '&:hover': { boxShadow: 6 } }}>
                <Stack spacing={1}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography fontWeight={700}>Case #{c.id}</Typography>
                    <Chip size="small" label={c.modality} color={c.modality === 'CT' ? 'primary' : 'warning'} />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">Pseudo ID: {c.patientPseudoId}</Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Chip size="small" color={c.status === 'COMPLETED' ? 'success' : c.status === 'FAILED' ? 'error' : 'default'} label={c.status} />
                    <Chip size="small" variant="outlined" label={c.modality === 'MRI' ? 'MRI heuristic-supported' : 'CT primary'} />
                    {c.origin === 'SEEDED_DEMO' && (
                      <Chip size="small" color="secondary" label={c.demoCategory ? `Seeded demo · ${c.demoCategory}` : 'Seeded demo'} />
                    )}
                    <Chip
                      size="small"
                      variant="outlined"
                      color={c.executionMode ? 'primary' : 'default'}
                      label={c.executionMode ? `Execution mode: ${c.executionMode}` : c.origin === 'SEEDED_DEMO' ? 'Execution mode: not applicable' : 'Execution mode: not run yet'}
                    />
                  </Stack>
                  <Typography variant="caption" color="text.secondary">Created {new Date(c.createdAt).toLocaleString()}</Typography>
                </Stack>
              </Card>
            </Grid2>
          ))}
        </Grid2>
      )}
    </Stack>
  )
}
