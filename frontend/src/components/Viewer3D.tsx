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

export function Viewer3D({ liverArtifactId, lesionArtifactId, findings }: { liverArtifactId: number | null; lesionArtifactId: number | null; findings: FindingItem[] }) {
  const [opacity, setOpacity] = useState(0.45)
  const [showLiver, setShowLiver] = useState(true)
  const [showLesion, setShowLesion] = useState(true)
  const [selected, setSelected] = useState<string | null>(null)
  const [canvasKey, setCanvasKey] = useState(0)
  const liverMesh = useAuthorizedObjectUrl(liverArtifactId ? `/api/files/${liverArtifactId}/download` : null)
  const lesionMesh = useAuthorizedObjectUrl(lesionArtifactId ? `/api/files/${lesionArtifactId}/download` : null)

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

  const suspiciousZones = findings.filter((finding) => finding.type === FINDING_TYPES.LESION)

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

    <Box data-testid="viewer-3d-canvas" sx={{ height: 500, borderRadius: 2, overflow: 'hidden', border: '1px solid #d4dce8' }}>
      <Canvas key={canvasKey} camera={{ position: [150, 120, 150], fov: 40 }}>
        <ambientLight intensity={0.6} />
        <directionalLight position={[1, 1, 1]} intensity={1} />
        <Suspense fallback={null}>
          {showLiver && liverMesh.objectUrl && <MeshAsset url={liverMesh.objectUrl} color="#879cb2" opacity={opacity} onSelect={() => setSelected('Liver mesh selected')} />}
          {showLesion && lesionArtifactId && lesionMesh.objectUrl && <MeshAsset url={lesionMesh.objectUrl} color="#ef3d58" opacity={0.95} onSelect={() => setSelected('Lesion selected · detailed metadata endpoint not wired')} />}
        </Suspense>
        <OrbitControls makeDefault />
      </Canvas>
    </Box>

    {selected && <Alert severity="success">{selected}</Alert>}
    {suspiciousZones.length > 0 && (
      <Card sx={{ p: 2 }}>
        <Stack spacing={1}>
          <Typography variant="subtitle1">Suspicious zones</Typography>
          {suspiciousZones.map((finding) => (
            <Alert key={finding.id} severity={finding.confidence && finding.confidence >= 0.5 ? 'error' : 'warning'}>
              {finding.label} · confidence {finding.confidence ?? 'N/A'} · volume {finding.volumeMm3 ?? 'N/A'} mm3
            </Alert>
          ))}
        </Stack>
      </Card>
    )}
  </Stack>
}
