import type { AxiosError } from 'axios'
import { Alert, Box, Button, Card, CardContent, Chip, Grid2, List, ListItem, ListItemText, Stack, TextField, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'

type AdminSummary = {
  executionMode: string
  currentUserRole: string
  mlService: {
    status: string
    mode: string
    version: string
    liverModelConfigured: boolean
    lesionModelConfigured: boolean
    mriHeuristicSupported: boolean
  }
  users: Array<{ id: number; email: string; role: string }>
  demoCases: Array<{
    caseId: number
    caseSlug: string | null
    manifestVersion: string | null
    patientPseudoId: string
    modality: 'CT' | 'MRI'
    category: 'NORMAL' | 'SINGLE_LESION' | 'MULTIFOCAL' | 'DIFFICULT' | null
    sourceDataset: string | null
    updatedAt: string
  }>
}

type AdminCase = {
  id: number
  patientPseudoId: string
  modality: 'CT' | 'MRI'
  status: string
  origin: 'LIVE_PROCESSED' | 'SEEDED_DEMO'
  ownerEmail: string | null
  createdAt: string
  updatedAt: string
}

const GRAFANA_URL = (import.meta.env.VITE_GRAFANA_URL as string | undefined) || 'http://localhost:3000'
const PROMETHEUS_URL = (import.meta.env.VITE_PROMETHEUS_URL as string | undefined) || 'http://localhost:9090'
const GRAFANA_DASHBOARD = `${GRAFANA_URL}/d/mrt-overview/mrt-service-overview?kiosk&theme=dark&refresh=10s`

export function AdminPage() {
  const [summary, setSummary] = useState<AdminSummary | null>(null)
  const [allCases, setAllCases] = useState<AdminCase[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [manifestText, setManifestText] = useState('')
  const [importState, setImportState] = useState<'idle' | 'submitting'>('idle')
  const [importError, setImportError] = useState<string | null>(null)
  const [importResult, setImportResult] = useState<{ caseId: number; caseSlug: string; action: string } | null>(null)

  const loadSummary = async () => {
    try {
      const response = await api.get('/admin/summary')
      setSummary(response.data)
      setError(null)
      try {
        const casesResponse = await api.get('/admin/cases')
        setAllCases(casesResponse.data)
      } catch {
        setAllCases(null)
      }
    } catch (requestError) {
      const axiosError = requestError as AxiosError
      if (axiosError.response?.status === 403) {
        setError('Admin access required. Current account does not have permission to open this console.')
        return
      }
      setError('Unable to load admin summary. Backend may be unavailable or access is denied.')
    }
  }

  useEffect(() => {
    loadSummary().catch(() => setError('Unable to load admin summary. Backend may be unavailable or access is denied.'))
  }, [])

  const importManifest = async () => {
    setImportError(null)
    setImportResult(null)
    let payload: unknown
    try {
      payload = JSON.parse(manifestText)
    } catch {
      setImportError('Manifest must be valid JSON before import.')
      return
    }

    setImportState('submitting')
    try {
      const response = await api.post('/admin/demo-cases/import', payload)
      setImportResult({
        caseId: response.data.caseId,
        caseSlug: response.data.caseSlug,
        action: response.data.action
      })
      await loadSummary()
    } catch (requestError) {
      const axiosError = requestError as AxiosError<{ message?: string }>
      setImportError(axiosError.response?.data?.message ?? 'Demo manifest import failed.')
    } finally {
      setImportState('idle')
    }
  }

  return <Stack spacing={2}>
    <Typography variant="h4">Admin & capability center</Typography>
    {error ? <Alert severity="error">{error}</Alert> : <Alert severity="info">Operational console: full case inventory across owners, seeded-demo import, capability snapshot, and service metrics. User and config management are intentionally read-only here (mutations stay server-side).</Alert>}

    <Grid2 container spacing={2}>
      <Grid2 size={{ xs: 12, md: 6 }}><Card><CardContent><Typography variant="h6">Execution profiles</Typography><Stack direction="row" spacing={1}><Chip label={`mode: ${summary?.executionMode ?? 'unknown'}`}/><Chip color="success" label={summary?.currentUserRole ?? 'role: unknown'}/><Chip color={summary?.mlService?.status === 'UP' ? 'success' : 'error'} label={`ml: ${summary?.mlService?.status ?? 'unknown'}`} /></Stack><Typography variant="body2" color="text.secondary">Defaults and profile switching are backend-configured.</Typography><Typography variant="body2" color="text.secondary" mt={1}>ML version: {summary?.mlService?.version ?? 'unknown'} · liver model: {summary?.mlService?.liverModelConfigured ? 'configured' : 'fallback'} · lesion model: {summary?.mlService?.lesionModelConfigured ? 'configured' : 'fallback'} · MRI heuristic: {summary?.mlService?.mriHeuristicSupported ? 'on' : 'off'}</Typography></CardContent></Card></Grid2>
      <Grid2 size={{ xs: 12, md: 6 }}><Card><CardContent><Typography variant="h6">Model capability snapshot</Typography><List dense><ListItem><ListItemText primary="CT liver segmentation" secondary="Implemented in backend/ML service" /></ListItem><ListItem><ListItemText primary="CT lesion segmentation" secondary="Model-backed when nnUNet is configured, heuristic fallback otherwise" /></ListItem><ListItem><ListItemText primary="MRI suspicious-zone analysis" secondary="Honest-ready via heuristic-supported path; dedicated model-backed extension remains optional" /></ListItem></List></CardContent></Card></Grid2>
      <Grid2 size={{ xs: 12, md: 12 }}>
        <Card>
          <CardContent>
            <Typography variant="h6">Demo manifest import</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>Paste a typed demo manifest JSON and import or update the seeded case idempotently.</Typography>
            <Stack spacing={1.5} sx={{ mt: 2 }}>
              <TextField
                multiline
                minRows={10}
                label="Demo manifest JSON"
                value={manifestText}
                onChange={(event) => setManifestText(event.target.value)}
                placeholder='{"schemaVersion":"v1","caseSlug":"ct-demo-001",...}'
              />
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                <Button variant="contained" onClick={importManifest} disabled={importState === 'submitting' || manifestText.trim() === ''}>
                  {importState === 'submitting' ? 'Importing…' : 'Import demo manifest'}
                </Button>
                {importResult && (
                  <Button component={Link} to={`/cases/${importResult.caseId}`} variant="outlined">
                    Open case #{importResult.caseId}
                  </Button>
                )}
              </Stack>
              {importError && <Alert severity="error">{importError}</Alert>}
              {importResult && <Alert severity="success">{importResult.action} seeded case {importResult.caseSlug} as case #{importResult.caseId}.</Alert>}
            </Stack>
          </CardContent>
        </Card>
      </Grid2>
      <Grid2 size={{ xs: 12, md: 12 }}>
        <Card>
          <CardContent>
            <Typography variant="h6">Ready demo studies</Typography>
            {!summary?.demoCases?.length ? (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5 }}>No seeded demo cases are currently imported.</Typography>
            ) : (
              <List dense>
                {summary.demoCases.map((demoCase) => (
                  <ListItem
                    key={demoCase.caseId}
                    secondaryAction={
                      <Button component={Link} to={`/cases/${demoCase.caseId}`} size="small" variant="outlined">
                        Open
                      </Button>
                    }
                  >
                    <ListItemText
                      primary={`${demoCase.caseSlug ?? `case-${demoCase.caseId}`} · ${demoCase.modality}${demoCase.category ? ` · ${demoCase.category}` : ''}`}
                      secondary={`manifest ${demoCase.manifestVersion ?? 'n/a'} · pseudo ID ${demoCase.patientPseudoId}${demoCase.sourceDataset ? ` · ${demoCase.sourceDataset}` : ''} · updated ${new Date(demoCase.updatedAt).toLocaleString()}`}
                    />
                  </ListItem>
                ))}
              </List>
            )}
          </CardContent>
        </Card>
      </Grid2>
      <Grid2 size={{ xs: 12, md: 12 }}>
        <Card><CardContent>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography variant="h6">All cases (operational)</Typography>
            <Chip size="small" label={`${allCases?.length ?? 0} total`} />
          </Stack>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>Every case across all owners — admin-only visibility (your Cases page shows only your own + seeded studies).</Typography>
          {!allCases?.length ? (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5 }}>No cases yet, or admin case list unavailable.</Typography>
          ) : (
            <List dense data-testid="admin-all-cases">
              {allCases.map((c) => (
                <ListItem key={c.id} secondaryAction={<Button component={Link} to={`/cases/${c.id}`} size="small" variant="outlined">Open</Button>}>
                  <ListItemText
                    primary={`Case #${c.id} · ${c.modality} · ${c.patientPseudoId}`}
                    secondary={`${c.status} · ${c.origin} · owner ${c.ownerEmail ?? 'n/a'} · created ${new Date(c.createdAt).toLocaleString()}`}
                  />
                </ListItem>
              ))}
            </List>
          )}
        </CardContent></Card>
      </Grid2>
      <Grid2 size={{ xs: 12, md: 12 }}>
        <Card><CardContent>
          <Stack direction="row" justifyContent="space-between" alignItems="center" flexWrap="wrap" useFlexGap>
            <Typography variant="h6">Service metrics (Grafana)</Typography>
            <Stack direction="row" spacing={1}>
              <Button size="small" variant="outlined" href={GRAFANA_URL} target="_blank" rel="noreferrer">Open Grafana</Button>
              <Button size="small" variant="outlined" href={PROMETHEUS_URL} target="_blank" rel="noreferrer">Prometheus</Button>
            </Stack>
          </Stack>
          <Alert severity="info" sx={{ mt: 1 }}>
            Prometheus + Grafana run from the observability compose (<code>docker compose -f docker-compose.yml -f docker-compose.observability.yml up</code>). The dashboard below embeds when Grafana is reachable at {GRAFANA_URL}.
          </Alert>
          <Box sx={{ mt: 1.5, border: '1px solid', borderColor: 'divider', borderRadius: 2, overflow: 'hidden' }}>
            <Box component="iframe" title="MRT Grafana dashboard" src={GRAFANA_DASHBOARD} sx={{ width: '100%', height: 520, border: 0, display: 'block' }} />
          </Box>
        </CardContent></Card>
      </Grid2>
      <Grid2 size={{ xs: 12, md: 12 }}><Card><CardContent><Typography variant="h6">Registered users</Typography>{summary ? <List dense>{summary.users.map((user) => <ListItem key={user.id}><ListItemText primary={user.email} secondary={`role ${user.role} · userId ${user.id}`} /></ListItem>)}</List> : <Typography variant="body2" color="text.secondary">User list unavailable until admin summary is loaded.</Typography>}</CardContent></Card></Grid2>
    </Grid2>
  </Stack>
}
