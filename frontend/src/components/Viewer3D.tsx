import { Alert, Box, Button, Card, FormControlLabel, Grid2, Slider, Stack, Switch, Typography } from '@mui/material'
import { OrbitControls, useGLTF } from '@react-three/drei'
import { Canvas } from '@react-three/fiber'
import { Suspense, useMemo, useState } from 'react'
import * as THREE from 'three'

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

export function Viewer3D({ liverArtifactId, lesionArtifactId }: { liverArtifactId: number | null; lesionArtifactId: number | null }) {
  const [opacity, setOpacity] = useState(0.45)
  const [showLiver, setShowLiver] = useState(true)
  const [showLesion, setShowLesion] = useState(true)
  const [selected, setSelected] = useState<string | null>(null)
  const [canvasKey, setCanvasKey] = useState(0)

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

  return <Stack spacing={1.5}>
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
        <Alert severity="info" sx={{ py: 0 }}>Format support: GLB/GLTF only</Alert>
      </Grid2>
    </Grid2>

    {!lesionArtifactId && <Alert severity="warning">Lesion mesh unavailable. Liver mesh remains fully viewable; lesion interaction metadata is partial.</Alert>}

    <Box sx={{ height: 500, borderRadius: 2, overflow: 'hidden', border: '1px solid #d4dce8' }}>
      <Canvas key={canvasKey} camera={{ position: [150, 120, 150], fov: 40 }}>
        <ambientLight intensity={0.6} />
        <directionalLight position={[1, 1, 1]} intensity={1} />
        <Suspense fallback={null}>
          {showLiver && <MeshAsset url={`http://localhost:8080/api/files/${liverArtifactId}/download`} color="#879cb2" opacity={opacity} onSelect={() => setSelected('Liver mesh selected')} />}
          {showLesion && lesionArtifactId && <MeshAsset url={`http://localhost:8080/api/files/${lesionArtifactId}/download`} color="#ef3d58" opacity={0.95} onSelect={() => setSelected('Lesion selected · detailed metadata endpoint not wired')} />}
        </Suspense>
        <OrbitControls makeDefault />
      </Canvas>
    </Box>

    {selected && <Alert severity="success">{selected}</Alert>}
  </Stack>
}
