import { Alert, Box, Button, FormControlLabel, Slider, Stack, Switch, Typography } from '@mui/material'
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

  if (!liverArtifactId) return <Alert severity="info">Liver mesh artifact is unavailable. Run real pipeline first.</Alert>

  return <Stack spacing={1.5}>
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} alignItems={{ xs: 'flex-start', md: 'center' }}>
      <Typography variant="body2">Opacity</Typography>
      <Slider min={0.1} max={0.9} step={0.05} value={opacity} onChange={(_, v)=>setOpacity(Number(v))} sx={{ maxWidth: 220 }} />
      <FormControlLabel control={<Switch checked={showLiver} onChange={(_, v)=>setShowLiver(v)} />} label="Show liver" />
      <FormControlLabel control={<Switch checked={showLesion} onChange={(_, v)=>setShowLesion(v)} />} label="Show lesion" />
      <Button size="small" variant="outlined" onClick={() => setCanvasKey((v) => v + 1)}>Reset camera</Button>
      <Button size="small" variant="outlined" onClick={exportShot}>Export screenshot</Button>
    </Stack>

    <Stack direction="row" spacing={1}><Alert severity="info" sx={{ py: 0 }}>Format support: GLB/GLTF only in current frontend loader.</Alert>{!lesionArtifactId && <Alert severity="warning" sx={{ py: 0 }}>Lesion mesh unavailable (no lesion or no lesion model output).</Alert>}</Stack>

    <Box sx={{ height: 460, borderRadius: 2, overflow: 'hidden', border: '1px solid #d4dce8' }}>
      <Canvas key={canvasKey} camera={{ position: [150, 120, 150], fov: 40 }}>
        <ambientLight intensity={0.6} />
        <directionalLight position={[1, 1, 1]} intensity={1} />
        <Suspense fallback={null}>
          {showLiver && <MeshAsset url={`http://localhost:8080/api/files/${liverArtifactId}/download`} color="#879cb2" opacity={opacity} onSelect={() => setSelected('Liver mesh selected')} />}
          {showLesion && lesionArtifactId && <MeshAsset url={`http://localhost:8080/api/files/${lesionArtifactId}/download`} color="#ef3d58" opacity={0.95} onSelect={() => setSelected('Lesion mesh selected (metadata endpoint not yet wired)')} />}
        </Suspense>
        <OrbitControls makeDefault />
      </Canvas>
    </Box>

    <Stack direction="row" spacing={1}>
      <ChipLegend color="#879cb2" label="Liver" />
      <ChipLegend color="#ef3d58" label="Lesion" />
    </Stack>
    {selected && <Alert severity="success">{selected}</Alert>}
  </Stack>
}

function ChipLegend({ color, label }: { color: string; label: string }) {
  return <Stack direction="row" alignItems="center" spacing={1}><Box sx={{ width: 12, height: 12, bgcolor: color, borderRadius: '50%' }} /><Typography variant="caption">{label}</Typography></Stack>
}
