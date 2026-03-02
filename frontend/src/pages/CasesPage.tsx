import { Alert, Box, Card, Chip, Grid2, MenuItem, Skeleton, Stack, TextField, Typography } from '@mui/material'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { CaseItem } from '../types'

export function CasesPage() {
  const [items, setItems] = useState<CaseItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [modality, setModality] = useState('ALL')
  const [status, setStatus] = useState('ALL')

  useEffect(() => {
    api.get('/cases')
      .then((r) => setItems(r.data))
      .catch(() => setError('Cannot load cases. Please verify authentication and backend health.'))
      .finally(() => setLoading(false))
  }, [])

  const filtered = useMemo(() => items.filter((c) =>
    (modality === 'ALL' || c.modality === modality) &&
    (status === 'ALL' || c.status === status) &&
    (query === '' || `${c.id}${c.patientPseudoId}`.toLowerCase().includes(query.toLowerCase()))
  ), [items, query, modality, status])

  return <Stack spacing={2.5}>
    <Stack direction="row" justifyContent="space-between" alignItems="flex-end">
      <Box><Typography variant="h4">Case operations</Typography><Typography color="text.secondary">Track workflows, run inference, and inspect artifacts with honest status messaging.</Typography></Box>
      <Chip color="primary" label={`${items.length} total`} />
    </Stack>

    <Card sx={{ p: 2 }}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <TextField label="Search case ID / pseudo ID" value={query} onChange={(e)=>setQuery(e.target.value)} fullWidth />
        <TextField select label="Modality" value={modality} onChange={(e)=>setModality(e.target.value)} sx={{ minWidth: 150 }}>
          <MenuItem value="ALL">All</MenuItem><MenuItem value="CT">CT</MenuItem><MenuItem value="MRI">MRI</MenuItem>
        </TextField>
        <TextField select label="Status" value={status} onChange={(e)=>setStatus(e.target.value)} sx={{ minWidth: 160 }}>
          <MenuItem value="ALL">All</MenuItem><MenuItem value="CREATED">CREATED</MenuItem><MenuItem value="PROCESSING">PROCESSING</MenuItem><MenuItem value="COMPLETED">COMPLETED</MenuItem><MenuItem value="FAILED">FAILED</MenuItem>
        </TextField>
      </Stack>
    </Card>

    {loading && <Grid2 container spacing={2}>{Array.from({ length: 6 }).map((_,i)=><Grid2 key={i} size={{xs:12,md:6,lg:4}}><Card sx={{p:2}}><Skeleton height={28}/><Skeleton/><Skeleton width="40%"/></Card></Grid2>)}</Grid2>}
    {error && <Alert severity="error">{error}</Alert>}
    {!loading && !filtered.length && <Alert severity="info">No cases match the selected filters.</Alert>}

    <Grid2 container spacing={2}>
      {filtered.map((c) => (
        <Grid2 size={{ xs: 12, md: 6, lg: 4 }} key={c.id}>
          <Card component={Link} to={`/cases/${c.id}`} sx={{ p: 2, textDecoration: 'none', display: 'block', '&:hover': { transform: 'translateY(-2px)', boxShadow: 4 } }}>
            <Stack spacing={1}>
              <Stack direction="row" justifyContent="space-between"><Typography fontWeight={700}>Case #{c.id}</Typography><Chip size="small" label={c.modality} /></Stack>
              <Typography color="text.secondary">{c.patientPseudoId}</Typography>
              <Stack direction="row" spacing={1}>
                <Chip size="small" color={c.status === 'COMPLETED' ? 'success' : c.status === 'FAILED' ? 'error' : 'default'} label={c.status} />
                <Chip size="small" variant="outlined" label={c.modality === 'MRI' ? 'MRI experimental' : 'CT standard'} />
              </Stack>
              <Typography variant="caption" color="text.secondary">Created {new Date(c.createdAt).toLocaleString()}</Typography>
            </Stack>
          </Card>
        </Grid2>
      ))}
    </Grid2>
  </Stack>
}
