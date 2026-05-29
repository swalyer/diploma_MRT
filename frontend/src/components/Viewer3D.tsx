import { Alert, Box, Button, Card, FormControlLabel, Grid2, Slider, Stack, Switch, Typography } from '@mui/material'
import { OrbitControls, useGLTF } from '@react-three/drei'
import { Canvas } from '@react-three/fiber'
import { Suspense, useEffect, useMemo, useState } from 'react'
import * as THREE from 'three'
import { authorizedFetch } from '../api/client'
import { FINDING_TYPES, type FindingItem } from '../types'

function useAuthorizedObjectUrl(path: string | null) {
  const [objectUrl, setObjectUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!path) {
      setObjectUrl(null)
      setError(null)
      return
    }
    let active = true
    let nextObjectUrl: string | null = null
    const load = async () => {
      try {
        const response = await authorizedFetch(path)
        if (!response.ok) throw new Error(`HTTP ${response.status}`)
        const blob = await response.blob()
        nextObjectUrl = URL.createObjectURL(blob)
        if (active) {
          setObjectUrl(nextObjectUrl)
          setError(null)
        }
      } catch (loadError) {
        if (active) {
          setObjectUrl(null)
          setError(loadError instanceof Error ? loadError.message : 'Mesh load failed')
        }
      }
    }
    load().catch(() => setError('Mesh load failed'))
    return () => {
      active = false
      if (nextObjectUrl) URL.revokeObjectURL(nextObjectUrl)
    }
  }, [path])

  return { objectUrl, error }
}

function MeshAsset({ url, color, opacity, onSelect }: { url: string; color: string; opacity: number; onSelect: () => void }) {
  const gltf = useGLTF(url)
  const scene = useMemo(() => {
    const clone = gltf.scene.clone()
    clone.traverse((obj) => {
      if ((obj as THREE.Mesh).isMesh) {
        ;(obj as THREE.Mesh).material = new THREE.MeshStandardMaterial({ color, transparent: true, opacity })
      }
    })
    return clone
  }, [gltf.scene, color, opacity])
  return <primitive object={scene} onClick={onSelect} />
}

export function Viewer3D({
  liverArtifactId,
  lesionArtifactId,
  findings,
  selectedFindingId,
  onSelectFinding,
}: {
  liverArtifactId: number | null
  lesionArtifactId: number | null
  findings: FindingItem[]
  selectedFindingId: number | null
  onSelectFinding?: (findingId: number) => void
}) {
  const [opacity, setOpacity] = useState(0.45)
  const [showLiver, setShowLiver] = useState(true)
  const [showLesion, setShowLesion] = useState(true)
  const [selectionMessage, setSelectionMessage] = useState<string | null>(null)
  const [canvasKey, setCanvasKey] = useState(0)
  const liverMesh = useAuthorizedObjectUrl(liverArtifactId ? `/api/files/${liverArtifactId}/download` : null)
  const lesionMesh = useAuthorizedObjectUrl(lesionArtifactId ? `/api/files/${lesionArtifactId}/download` : null)
  const suspiciousZones = findings.filter((finding) => finding.type === FINDING_TYPES.LESION)

  const exportShot = () => {
    const canvas = document.querySelector('canvas')
    if (!canvas) return
    const link = document.createElement('a')
    link.href = canvas.toDataURL('image/png')
    link.download = 'viewer3d-screenshot.png'
    link.click()
  }

  if (!liverArtifactId) {
    return (
      <Card sx={{ p: 2 }}>
        <Stack spacing={1.5}>
          <Typography variant="h6">3D mesh workspace unavailable</Typography>
          <Alert severity="info">No liver mesh artifact is available for this case.</Alert>
          <Typography variant="body2" color="text.secondary">To enable 3D view: upload source artifacts, run pipeline, and ensure mesh generation stage completes.</Typography>
          <Typography variant="body2" color="text.secondary">Execution mode and failure reason details are partial because backend does not expose explicit mesh-stage reason codes yet.</Typography>
        </Stack>
      </Card>
    )
  }

  const selectedFinding = suspiciousZones.find((finding) => finding.id === selectedFindingId) ?? null
  const missingLesionMessage = !lesionArtifactId
    ? suspiciousZones.length === 0
      ? 'No suspicious-zone mesh is available because no lesion findings were materialized for this case.'
      : 'Structured suspicious-zone findings exist, but no lesion mesh artifact was materialized for this case.'
    : null
  const formatVector = (values: number[] | null | undefined) => values?.map((value) => Number(value).toFixed(2)).join(', ') ?? 'N/A'

  return <Stack spacing={1.5} data-testid="viewer-3d-root">
    <Grid2 container spacing={1}>
      <Grid2 size={{ xs: 12, lg: 8 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} alignItems={{ xs: 'flex-start', md: 'center' }}>
          <Typography variant="body2">Liver opacity</Typography>
          <Slider min={0.1} max={0.9} step={0.05} value={opacity} onChange={(_, v) => setOpacity(Number(v))} sx={{ maxWidth: 220 }} />
          <FormControlLabel control={<Switch checked={showLiver} onChange={(_, v) => setShowLiver(v)} />} label="Show liver" />
          <FormControlLabel control={<Switch checked={showLesion} onChange={(_, v) => setShowLesion(v)} />} label="Show lesion" disabled={!lesionArtifactId} />
          <Button size="small" variant="outlined" onClick={() => setCanvasKey((v) => v + 1)}>Reset camera</Button>
          <Button size="small" variant="outlined" onClick={exportShot}>Export screenshot</Button>
        </Stack>
      </Grid2>
      <Grid2 size={{ xs: 12, lg: 4 }}>
        <Alert severity="info" sx={{ py: 0 }} data-testid="viewer-3d-format-alert">Format support: GLB/GLTF only</Alert>
      </Grid2>
    </Grid2>

    {!lesionArtifactId && <Alert severity="warning">Lesion mesh unavailable. Liver mesh remains fully viewable; lesion interaction metadata is partial.</Alert>}
    {liverMesh.error && <Alert severity="error">Failed to load liver mesh: {liverMesh.error}</Alert>}
    {lesionMesh.error && <Alert severity="warning">Failed to load lesion mesh: {lesionMesh.error}</Alert>}

    <Box data-testid="viewer-3d-canvas" sx={{ height: 500, borderRadius: 2, overflow: 'hidden', border: '1px solid', borderColor: 'divider' }}>
      <Canvas key={canvasKey} camera={{ position: [150, 120, 150], fov: 40 }}>
        <ambientLight intensity={0.6} />
        <directionalLight position={[1, 1, 1]} intensity={1} />
        <Suspense fallback={null}>
          {showLiver && liverMesh.objectUrl && <MeshAsset url={liverMesh.objectUrl} color="#879cb2" opacity={opacity} onSelect={() => setSelectionMessage('Liver mesh selected')} />}
          {showLesion && lesionArtifactId && lesionMesh.objectUrl && (
            <MeshAsset
              url={lesionMesh.objectUrl}
              color="#ef3d58"
              opacity={0.95}
              onSelect={() => {
                if (suspiciousZones.length === 1) {
                  onSelectFinding?.(suspiciousZones[0].id)
                }
                setSelectionMessage(
                  suspiciousZones.length > 0
                    ? 'Lesion mesh selected. Inspect the structured suspicious-zone metadata below.'
                    : 'Lesion mesh selected.'
                )
              }}
            />
          )}
        </Suspense>
        <OrbitControls makeDefault />
      </Canvas>
    </Box>

    {missingLesionMessage && <Alert severity="warning">{missingLesionMessage}</Alert>}
    {selectionMessage && <Alert severity="success">{selectionMessage}</Alert>}
    {suspiciousZones.length > 0 && (
      <Card sx={{ p: 2 }}>
        <Stack spacing={1}>
          <Typography variant="subtitle1">Suspicious zones</Typography>
          {suspiciousZones.map((finding) => (
            <Stack key={finding.id} direction={{ xs: 'column', md: 'row' }} spacing={1} alignItems={{ xs: 'stretch', md: 'center' }}>
              <Alert sx={{ flex: 1, mb: 0 }} severity={finding.confidence && finding.confidence >= 0.5 ? 'error' : 'warning'}>
                {finding.label} · confidence {finding.confidence ?? 'N/A'} · volume {finding.volumeMm3 ?? 'N/A'} mm3
              </Alert>
              <Button
                size="small"
                variant={selectedFindingId === finding.id ? 'contained' : 'outlined'}
                onClick={() => onSelectFinding?.(finding.id)}
              >
                Inspect
              </Button>
            </Stack>
          ))}
        </Stack>
      </Card>
    )}
    {selectedFinding && (
      <Card sx={{ p: 2 }}>
        <Stack spacing={0.75}>
          <Typography variant="subtitle1">Selected Suspicious Zone</Typography>
          <Typography variant="body2">Label: {selectedFinding.label}</Typography>
          <Typography variant="body2">Confidence: {selectedFinding.confidence ?? 'N/A'}</Typography>
          <Typography variant="body2">Volume: {selectedFinding.volumeMm3 ?? 'N/A'} mm3</Typography>
          <Typography variant="body2">Size: {selectedFinding.sizeMm ?? 'N/A'} mm</Typography>
          <Typography variant="body2">Support: {selectedFinding.location?.suspicion ?? 'artifact-backed'}</Typography>
          <Typography variant="body2">Segment: {selectedFinding.location?.segment ?? 'N/A'}</Typography>
          <Typography variant="body2">Centroid: {formatVector(selectedFinding.location?.centroid ?? null)}</Typography>
          <Typography variant="body2">
            Bounding box: min [{formatVector(selectedFinding.location?.bbox?.min ?? null)}] · max [{formatVector(selectedFinding.location?.bbox?.max ?? null)}]
          </Typography>
          <Typography variant="body2">Extent: {formatVector(selectedFinding.location?.extent ?? null)}</Typography>
        </Stack>
      </Card>
    )}
  </Stack>
}
