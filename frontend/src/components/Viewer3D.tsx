import { Alert, Button, FormControlLabel, Slider, Stack, Switch, Typography } from '@mui/material'
import { OrbitControls, useGLTF } from '@react-three/drei'
import { Canvas } from '@react-three/fiber'
import { Suspense, useMemo, useState } from 'react'
import * as THREE from 'three'

function MeshAsset({ url, color, opacity }: { url: string; color: string; opacity: number }) {
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
  return <primitive object={scene} />
}

export function Viewer3D({ liverArtifactId, lesionArtifactId }: { liverArtifactId: number | null; lesionArtifactId: number | null }) {
  const [opacity, setOpacity] = useState(0.45)
  const [showLesion, setShowLesion] = useState(true)

  if (!liverArtifactId) {
    return <Alert severity="info">Liver mesh artifact is not available yet. Run inference in real mode first.</Alert>
  }

  return <Stack spacing={1.5}>
    <Stack direction="row" spacing={2} alignItems="center">
      <Typography variant="body2">Liver opacity</Typography>
      <Slider min={0.1} max={0.9} step={0.05} value={opacity} onChange={(_, v)=>setOpacity(Number(v))} sx={{ maxWidth: 220 }} />
      <FormControlLabel control={<Switch checked={showLesion} onChange={(_, v)=>setShowLesion(v)} />} label="Show lesion" />
      <Button size="small" variant="outlined" onClick={() => window.location.reload()}>Reset camera</Button>
    </Stack>
    <div style={{ height: 420, borderRadius: 12, overflow: 'hidden', border: '1px solid #d4dce8' }}>
      <Canvas camera={{ position: [150, 120, 150], fov: 40 }}>
        <ambientLight intensity={0.6} />
        <directionalLight position={[1, 1, 1]} intensity={1} />
        <Suspense fallback={null}>
          <MeshAsset url={`http://localhost:8080/api/files/${liverArtifactId}/download`} color="#7f93aa" opacity={opacity} />
          {showLesion && lesionArtifactId && <MeshAsset url={`http://localhost:8080/api/files/${lesionArtifactId}/download`} color="#ef3d58" opacity={0.9} />}
        </Suspense>
        <OrbitControls makeDefault />
      </Canvas>
    </div>
    {!lesionArtifactId && <Alert severity="warning">Lesion mesh missing: lesion model may be unconfigured or no lesion detected.</Alert>}
  </Stack>
}
