import { Alert, Box, Card, Chip, CircularProgress, Grid2, MenuItem, Stack, TextField, Typography } from '@mui/material'
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

  useEffect(() => {
    api.get('/cases')
      .then((r) => setItems(r.data))
      .catch(() => setError('Cannot load cases.'))
      .finally(() => setLoading(false))
  }, [])

  const filtered = useMemo(() => items.filter((c) =>
    (modality === 'ALL' || c.modality === modality) &&
    (query === '' || `${c.id}${c.patientPseudoId}`.toLowerCase().includes(query.toLowerCase()))
  ), [items, query, modality])

  return <Stack spacing={2}>
    <Typography variant="h4" fontWeight={700}>Cases</Typography>
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
      <TextField label="Search by case or pseudo ID" value={query} onChange={(e)=>setQuery(e.target.value)} fullWidth />
      <TextField select label="Modality" value={modality} onChange={(e)=>setModality(e.target.value)} sx={{ minWidth: 160 }}>
        <MenuItem value="ALL">All</MenuItem><MenuItem value="CT">CT</MenuItem><MenuItem value="MRI">MRI</MenuItem>
      </TextField>
    </Stack>
    {loading && <CircularProgress />}
    {error && <Alert severity="error">{error}</Alert>}
    {!loading && !filtered.length && <Alert severity="info">No cases match your current filters.</Alert>}
    <Grid2 container spacing={2}>
      {filtered.map((c) => <Grid2 size={{ xs: 12, md: 6, lg: 4 }} key={c.id}>
        <Card component={Link} to={`/cases/${c.id}`} sx={{ p: 2, textDecoration: 'none', display: 'block', '&:hover': { boxShadow: 4 } }}>
          <Stack spacing={1}>
            <Stack direction="row" justifyContent="space-between"><Typography fontWeight={700}>Case #{c.id}</Typography><Chip size="small" label={c.modality} /></Stack>
            <Typography color="text.secondary">{c.patientPseudoId}</Typography>
            <Box><Chip size="small" color={c.status === 'COMPLETED' ? 'success' : 'default'} label={c.status} /></Box>
            <Typography variant="caption" color="text.secondary">{new Date(c.createdAt).toLocaleString()}</Typography>
          </Stack>
        </Card>
      </Grid2>)}
    </Grid2>
  </Stack>
}
