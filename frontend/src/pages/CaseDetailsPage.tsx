import { Alert, Button, Card, CardContent, Chip, CircularProgress, Stack, Tab, Tabs, Typography } from '@mui/material'
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

  const refresh = async () => {
    if (!id) return
    setLoading(true)
    const [s, a, r, f, v3] = await Promise.all([
      api.get(`/cases/${id}/status`),
      api.get(`/cases/${id}/artifacts`),
      api.get(`/cases/${id}/report`).catch(() => ({ data: { reportText: '' } })),
      api.get(`/cases/${id}/findings`).catch(() => ({ data: [] })),
      api.get(`/cases/${id}/viewer/3d`).catch(() => ({ data: { liverMeshArtifactId: null, lesionMeshArtifactId: null } }))
    ])
    setStatus(s.data); setArtifacts(a.data); setReport(r.data.reportText); setFindings(f.data); setViewer3d(v3.data)
    setLoading(false)
  }

  useEffect(() => { refresh().catch(() => setLoading(false)) }, [id])

  const start = async () => {
    if (!id) return
    await api.post(`/cases/${id}/process`)
    await refresh()
  }

  const mode = useMemo(() => artifacts.some((a) => a.type === 'LIVER_MASK') ? 'real' : 'mock', [artifacts])

  if (loading) return <CircularProgress />

  return <Stack spacing={2}>
    <Stack direction="row" justifyContent="space-between" alignItems="center">
      <Typography variant="h4" fontWeight={700}>Case #{id}</Typography>
      <Stack direction="row" spacing={1}><Chip label={status?.status ?? 'N/A'} /><Chip color={mode === 'real' ? 'success' : 'warning'} label={mode.toUpperCase()} /></Stack>
    </Stack>

    <Button variant="contained" onClick={start}>Run analysis</Button>
    <Tabs value={tab} onChange={(_, v)=>setTab(v)}>
      <Tab label="Overview" /><Tab label="2D imaging" /><Tab label="3D viewer" /><Tab label="Report" /><Tab label="Artifacts" />
    </Tabs>

    {tab === 0 && <Card><CardContent>
      <Stack spacing={1}>
        <Typography variant="h6">Pipeline timeline</Typography>
        {status?.stageAuditTrail?.length ? status.stageAuditTrail.map((e, idx) => <Typography key={idx} variant="body2">{new Date(e.at).toLocaleString()} — {e.action}</Typography>) : <Alert severity="info">No stages yet.</Alert>}
      </Stack>
    </CardContent></Card>}

    {tab === 1 && <Card><CardContent><Medical2DViewer artifacts={artifacts} /></CardContent></Card>}
    {tab === 2 && <Card><CardContent><Viewer3D liverArtifactId={viewer3d?.liverMeshArtifactId ?? null} lesionArtifactId={viewer3d?.lesionMeshArtifactId ?? null} /></CardContent></Card>}

    {tab === 3 && <Card><CardContent><Stack spacing={1}>
      <Alert severity="warning">Decision-support only. Not for standalone diagnosis.</Alert>
      <Typography whiteSpace="pre-wrap">{report || 'Report is not available yet.'}</Typography>
      {!!findings.length && findings.map((f) => <Typography key={f.id} variant="body2">{f.label}: volume {f.volumeMm3 ?? 'N/A'} mm³, confidence {f.confidence ?? 'N/A'}</Typography>)}
    </Stack></CardContent></Card>}

    {tab === 4 && <Card><CardContent>
      <Stack spacing={1}>{artifacts.map((a) => <a key={a.id} href={`http://localhost:8080${a.downloadUrl}`} target="_blank">{a.type}: {a.fileName}</a>)}</Stack>
    </CardContent></Card>}
  </Stack>
}
