import { Canvas } from '@react-three/fiber'
import { OrbitControls } from '@react-three/drei'
import { useState } from 'react'

export function Viewer3D() {
  const [opacity, setOpacity] = useState(0.5)
  const [showLesion, setShowLesion] = useState(true)
  return <div>
    <label>Opacity <input type="range" min="0.1" max="1" step="0.1" value={opacity} onChange={(e)=>setOpacity(Number(e.target.value))} /></label>
    <label><input type="checkbox" checked={showLesion} onChange={(e)=>setShowLesion(e.target.checked)} /> Show lesion</label>
    <div style={{height:300}}>
      <Canvas camera={{position:[2,2,2]}}>
        <ambientLight />
        <mesh><sphereGeometry args={[1, 32, 32]} /><meshStandardMaterial color={'#6aa8ff'} transparent opacity={opacity} /></mesh>
        {showLesion && <mesh position={[0.4,0.2,0.1]}><sphereGeometry args={[0.2, 16, 16]} /><meshStandardMaterial color={'red'} /></mesh>}
        <OrbitControls />
      </Canvas>
    </div>
  </div>
}
