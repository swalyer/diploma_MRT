import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid2,
  List,
  ListItem,
  ListItemText,
  Stack,
  Tab,
  Tabs,
  Typography
} from '@mui/material'
import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Medical2DViewer } from '../components/Medical2DViewer'
import { Viewer3D } from '../components/Viewer3D'
import type { ArtifactItem, CaseItem, FindingItem, StatusPayload, Viewer3DPayload } from '../types'

type PageState = 'loading' | 'error' | 'success' | 'success-degraded'

export function CaseDetailsPage() {
  const { id } = useParams()
  const [caseSummary, setCaseSummary] = useState<CaseItem | null>(null)
  const [report, setReport] = useState('')
  const [status, setStatus] = useState<StatusPayload | null>(null)
  const [artifacts, setArtifacts] = useState<ArtifactItem[]>([])
  const [findings, setFindings] = useState<FindingItem[]>([])
  const [viewer3d, setViewer3d] = useState<Viewer3DPayload | null>(null)
  const [tab, setTab] = useState(0)
  const [state, setState] = useState<PageState>('loading')
  const [error, setError] = useState<string | null>(null)

  const refresh = async () => {
    if (!id) return
    setState('loading')
    setError(null)

    try {
      const core = await Promise.all([
        api.get(`/cases/${id}`),
        api.get(`/cases/${id}/status`),
        api.get(`/cases/${id}/artifacts`),
        api.get(`/cases/${id}/viewer/3d`)
      ])

      setCaseSummary(core[0].data)
      setStatus(core[1].data)
      setArtifacts(core[2].data)
      setViewer3d(core[3].data)

      const [reportRes, findingsRes] = await Promise.allSettled([api.get(`/cases/${id}/report`), api.get(`/cases/${id}/findings`)])
      const degraded = reportRes.status === 'rejected' || findingsRes.status === 'rejected'
      setReport(reportRes.status === 'fulfilled' ? reportRes.value.data.reportText : '')
      setFindings(findingsRes.status === 'fulfilled' ? findingsRes.value.data : [])
      setState(degraded ? 'success-degraded' : 'success')
    } catch {
      setState('error')
      setError('Unable to load case core data. We only show a minimal-safe shell until verified data is available.')
    }
  }

  useEffect(() => {
    refresh().catch(() => setState('error'))
  }, [id])

  const start = async () => {
    if (!id || state === 'error') return
    await api.post(`/cases/${id}/process`)
    await refresh()
  }

  const capability = useMemo(
    () => ({
      mode: status?.executionMode ? `backend: ${status.executionMode}` : 'not exposed by API',
      liverMask: artifacts.some((a) => a.type === 'LIVER_MASK'),
      lesionMask: artifacts.some((a) => a.type === 'LESION_MASK'),
      liverMesh: Boolean(viewer3d?.liverMeshArtifactId),
      lesionMesh: Boolean(viewer3d?.lesionMeshArtifactId)
    }),
    [artifacts, viewer3d, status]
  )

  if (state === 'loading') return <Card><CardContent><Typography>Loading case workspace…</Typography></CardContent></Card>

  if (state === 'error') {
    return (
      <Card sx={{ p: 3 }}>
        <Stack spacing={2}>
          <Typography variant="h5">Case #{id}</Typography>
          <Alert severity="error">{error}</Alert>
          <Typography color="text.secondary">Verified details are unavailable. No inferred chips, model claims, or artifact assertions are rendered in this state.</Typography>
          <Box>
            <Button variant="contained" onClick={() => refresh()}>Retry case load</Button>
          </Box>
        </Stack>
      </Card>
    )
  }

  const canRunPipeline = Boolean(caseSummary?.id) && status?.status !== 'PROCESSING'

  return (
    <Stack spacing={2.5}>
      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Stack direction={{ xs: 'column', lg: 'row' }} justifyContent="space-between" spacing={2}>
              <Box>
                <Typography variant="h4">Case #{caseSummary?.id}</Typography>
                <Typography color="text.secondary">Pseudo ID: {caseSummary?.patientPseudoId} · Modality: {caseSummary?.modality ?? 'Unknown'}</Typography>
              </Box>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={`Status: ${status?.status ?? 'N/A'}`} />
                <Chip label={`Inference: ${status?.inferenceStatus ?? 'N/A'}`} />
                <Chip color="warning" label={capability.mode} />
                {state === 'success-degraded' && <Chip color="warning" label="Partial data loaded" />}
              </Stack>
            </Stack>

            <Grid2 container spacing={1}>
              <Grid2 size={{ xs: 12, lg: 8 }}>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  <Chip size="small" label={`Liver mask: ${capability.liverMask ? 'available' : 'unavailable'}`} color={capability.liverMask ? 'success' : 'default'} />
                  <Chip size="small" label={`Lesion mask: ${capability.lesionMask ? 'available' : 'unavailable'}`} color={capability.lesionMask ? 'success' : 'default'} />
                  <Chip size="small" label={`Liver mesh: ${capability.liverMesh ? 'available' : 'unavailable'}`} color={capability.liverMesh ? 'success' : 'default'} />
                  <Chip size="small" label={`Lesion mesh: ${capability.lesionMesh ? 'available' : 'unavailable'}`} color={capability.lesionMesh ? 'success' : 'default'} />
                  <Chip size="small" color="warning" label="MRI support is experimental" />
                </Stack>
              </Grid2>
              <Grid2 size={{ xs: 12, lg: 4 }}>
                <Stack direction="row" justifyContent={{ xs: 'flex-start', lg: 'flex-end' }}>
                  <Button variant="contained" onClick={start} disabled={!canRunPipeline}>Run / rerun pipeline</Button>
                </Stack>
              </Grid2>
            </Grid2>
          </Stack>
        </CardContent>
      </Card>

      <Tabs value={tab} onChange={(_, v) => setTab(v)}>
        <Tab label="Overview" />
        <Tab label="2D Imaging" />
        <Tab label="Report" />
        <Tab label="3D Viewer" />
        <Tab label="Artifacts / Technical" />
        <Tab label="Audit / Admin" />
      </Tabs>

      {tab === 0 && (
        <Grid2 container spacing={2}>
          <Grid2 size={{ xs: 12, lg: 7 }}>
            <Card><CardContent>
              <Typography variant="h6">Pipeline timeline</Typography>
              <Divider sx={{ my: 1.5 }} />
              {status?.stageAuditTrail?.length ? status.stageAuditTrail.map((e, idx) => (
                <Typography key={idx} variant="body2" sx={{ mb: 1 }}>{new Date(e.at).toLocaleString()} — {e.action}</Typography>
              )) : <Alert severity="info">No stage events available yet.</Alert>}
            </CardContent></Card>
          </Grid2>
          <Grid2 size={{ xs: 12, lg: 5 }}>
            <Card><CardContent>
              <Typography variant="h6">Execution summary</Typography>
              <Stack mt={1.5} spacing={1}>
                <Typography variant="body2">Findings count: {findings.length}</Typography>
                <Typography variant="body2">Model version: {status?.modelVersion || 'not exposed by API'}</Typography>
                <Typography variant="body2">Execution mode: {status?.executionMode || 'not exposed by API'}</Typography>
                <Typography variant="body2">Missing model weights: not explicitly surfaced by API</Typography>
              </Stack>
            </CardContent></Card>
          </Grid2>
        </Grid2>
      )}

      {tab === 1 && (
        <Card><CardContent><Medical2DViewer artifacts={artifacts} /></CardContent></Card>
      )}

      {tab === 2 && (
        <Card><CardContent>
          <Stack spacing={1.5}>
            <Alert severity="warning">Decision-support only. Not for standalone diagnosis.</Alert>
            <Stack direction="row" spacing={1}>
              <Button size="small" variant="outlined" onClick={() => navigator.clipboard.writeText(report || '')} disabled={!report}>Copy report</Button>
            </Stack>
            <Typography whiteSpace="pre-wrap">{report || 'Report unavailable (not returned by API).'}</Typography>
            <Divider />
            <Typography variant="subtitle2">Structured findings</Typography>
            {!findings.length ? <Alert severity="info">No structured findings returned.</Alert> : findings.map((f) => (
              <Typography key={f.id} variant="body2">{f.label} · volume {f.volumeMm3 ?? 'N/A'} mm³ · confidence {f.confidence ?? 'N/A'}</Typography>
            ))}
          </Stack>
        </CardContent></Card>
      )}

      {tab === 3 && (
        <Card><CardContent><Viewer3D liverArtifactId={viewer3d?.liverMeshArtifactId ?? null} lesionArtifactId={viewer3d?.lesionMeshArtifactId ?? null} /></CardContent></Card>
      )}

      {tab === 4 && (
        <Card><CardContent>
          <Typography variant="h6">Artifact inventory</Typography>
          {!artifacts.length ? (
            <Alert severity="info" sx={{ mt: 1.5 }}>No artifacts available. Pipeline output may be missing or run has not started.</Alert>
          ) : (
            <List dense>
              {artifacts.map((a) => (
                <ListItem
                  key={a.id}
                  secondaryAction={<Button size="small" href={`http://localhost:8080${a.downloadUrl}`} target="_blank">Download</Button>}
                >
                  <ListItemText primary={`${a.type} · ${a.fileName}`} secondary={`${a.mimeType} · artifactId ${a.id}`} />
                </ListItem>
              ))}
            </List>
          )}
          <Alert severity="info">Status payload now includes backend execution mode/model version when available; missing values are shown as unavailable.</Alert>
        </CardContent></Card>
      )}

      {tab === 5 && (
        <Card><CardContent>
          <Typography variant="h6">Audit / admin</Typography>
          <Typography variant="body2">Frontend exposes audit trail events and ownership-safe warnings. Role enforcement remains backend-controlled.</Typography>
        </CardContent></Card>
      )}
    </Stack>
  )
}
