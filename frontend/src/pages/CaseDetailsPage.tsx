import { Alert, Button, Card, CardContent, Chip, Divider, Grid2, List, ListItem, ListItemText, Stack, Tab, Tabs, Typography } from '@mui/material'
import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Medical2DViewer } from '../components/Medical2DViewer'
import { Viewer3D } from '../components/Viewer3D'
import type { ArtifactItem, FindingItem, StatusPayload, Viewer3DPayload } from '../types'

export function CaseDetailsPage() {
  const { id } = useParams()
  const [report, setReport] = useState('')
  const [status, setStatus] = useState<StatusPayload | null>(null)
  const [artifacts, setArtifacts] = useState<ArtifactItem[]>([])
  const [findings, setFindings] = useState<FindingItem[]>([])
  const [viewer3d, setViewer3d] = useState<Viewer3DPayload | null>(null)
  const [tab, setTab] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = async () => {
    if (!id) return
    setLoading(true); setError(null)
    try {
      const [s, a, r, f, v3] = await Promise.all([
        api.get(`/cases/${id}/status`),
        api.get(`/cases/${id}/artifacts`),
        api.get(`/cases/${id}/report`).catch(() => ({ data: { reportText: '' } })),
        api.get(`/cases/${id}/findings`).catch(() => ({ data: [] })),
        api.get(`/cases/${id}/viewer/3d`).catch(() => ({ data: { liverMeshArtifactId: null, lesionMeshArtifactId: null } }))
      ])
      setStatus(s.data); setArtifacts(a.data); setReport(r.data.reportText); setFindings(f.data); setViewer3d(v3.data)
    } catch {
      setError('Unable to load case details. Verify ownership, token validity, and backend health.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { refresh().catch(() => setLoading(false)) }, [id])

  const start = async () => {
    if (!id) return
    await api.post(`/cases/${id}/process`)
    await refresh()
  }

  const capability = useMemo(() => ({
    mode: artifacts.some((a) => a.type === 'LIVER_MASK') ? 'real-or-hybrid' : 'mock-or-pending',
    liverMask: artifacts.some((a) => a.type === 'LIVER_MASK'),
    lesionMask: artifacts.some((a) => a.type === 'LESION_MASK'),
    liverMesh: Boolean(viewer3d?.liverMeshArtifactId),
    lesionMesh: Boolean(viewer3d?.lesionMeshArtifactId)
  }), [artifacts, viewer3d])

  if (loading) return <Card><CardContent><Typography>Loading case...</Typography></CardContent></Card>

  return <Stack spacing={2.5}>
    {error && <Alert severity="error">{error}</Alert>}

    <Card><CardContent>
      <Stack spacing={2}>
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', md: 'center' }}>
          <Typography variant="h4">Case #{id}</Typography>
          <Stack direction="row" spacing={1}>
            <Chip label={status?.status ?? 'N/A'} />
            <Chip color={capability.mode === 'real-or-hybrid' ? 'success' : 'warning'} label={capability.mode} />
          </Stack>
        </Stack>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Chip size="small" label={`Liver mask: ${capability.liverMask ? 'available' : 'unavailable'}`} color={capability.liverMask ? 'success' : 'default'} />
          <Chip size="small" label={`Lesion mask: ${capability.lesionMask ? 'available' : 'unavailable'}`} color={capability.lesionMask ? 'success' : 'default'} />
          <Chip size="small" label={`Liver mesh: ${capability.liverMesh ? 'available' : 'unavailable'}`} color={capability.liverMesh ? 'success' : 'default'} />
          <Chip size="small" label={`Lesion mesh: ${capability.lesionMesh ? 'available' : 'unavailable'}`} color={capability.lesionMesh ? 'success' : 'default'} />
          <Chip size="small" label="MRI is experimental" color="warning" />
        </Stack>
        <Button variant="contained" onClick={start}>Run / rerun pipeline</Button>
      </Stack>
    </CardContent></Card>

    <Tabs value={tab} onChange={(_, v)=>setTab(v)}>
      <Tab label="Overview" /><Tab label="2D Imaging" /><Tab label="Report" /><Tab label="3D Viewer" /><Tab label="Artifacts / Technical" /><Tab label="Audit / Admin" />
    </Tabs>

    {tab === 0 && <Grid2 container spacing={2}>
      <Grid2 size={{ xs: 12, md: 6 }}><Card><CardContent><Typography variant="h6">Pipeline timeline</Typography><Divider sx={{ my: 1 }}/>{status?.stageAuditTrail?.length ? status.stageAuditTrail.map((e, idx) => <Typography key={idx} variant="body2">{new Date(e.at).toLocaleString()} — {e.action}</Typography>) : <Alert severity="info">No stage events yet.</Alert>}</CardContent></Card></Grid2>
      <Grid2 size={{ xs: 12, md: 6 }}><Card><CardContent><Typography variant="h6">Summary</Typography><Typography variant="body2">Findings count: {findings.length}</Typography><Typography variant="body2">Inference status: {status?.inferenceStatus ?? 'N/A'}</Typography><Typography variant="body2">Execution mode from API: not explicitly exposed yet; inferred from artifact presence only.</Typography></CardContent></Card></Grid2>
    </Grid2>}

    {tab === 1 && <Card><CardContent><Medical2DViewer artifacts={artifacts} /></CardContent></Card>}

    {tab === 2 && <Card><CardContent><Stack spacing={1.5}>
      <Alert severity="warning">Decision-support only. Not for standalone diagnosis.</Alert>
      <Stack direction="row" spacing={1}><Button size="small" variant="outlined" onClick={() => navigator.clipboard.writeText(report || '')}>Copy report</Button></Stack>
      <Typography whiteSpace="pre-wrap">{report || 'Report unavailable.'}</Typography>
      <Divider />
      <Typography variant="subtitle2">Structured findings</Typography>
      {!findings.length ? <Alert severity="info">No structured findings returned.</Alert> : findings.map((f) => <Typography key={f.id} variant="body2">{f.label} · volume {f.volumeMm3 ?? 'N/A'} mm³ · confidence {f.confidence ?? 'N/A'}</Typography>)}
    </Stack></CardContent></Card>}

    {tab === 3 && <Card><CardContent><Viewer3D liverArtifactId={viewer3d?.liverMeshArtifactId ?? null} lesionArtifactId={viewer3d?.lesionMeshArtifactId ?? null} /></CardContent></Card>}

    {tab === 4 && <Card><CardContent>
      <Typography variant="h6">Artifact inventory</Typography>
      {!artifacts.length ? <Alert severity="info">No artifacts available.</Alert> : <List dense>{artifacts.map((a) => <ListItem key={a.id} secondaryAction={<Button size="small" href={`http://localhost:8080${a.downloadUrl}`} target="_blank">Download</Button>}><ListItemText primary={`${a.type} · ${a.fileName}`} secondary={`${a.mimeType} · artifactId ${a.id}`} /></ListItem>)}</List>}
      <Alert severity="info">Model version and stage timings are not exposed by current backend status DTO; technical transparency is therefore partial.</Alert>
    </CardContent></Card>}

    {tab === 5 && <Card><CardContent><Typography variant="h6">Audit / admin</Typography><Typography variant="body2">Role-aware admin controls are backend-managed. Frontend currently exposes stage audit trail and capability warnings only.</Typography></CardContent></Card>}
  </Stack>
}
