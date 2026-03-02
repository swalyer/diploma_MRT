import { Canvas } from '@react-three/fiber'
import { OrbitControls } from '@react-three/drei'
import { useState } from 'react'

export function Viewer3D({ mode }: { mode: 'mock' | 'real' }) {
  const [opacity, setOpacity] = useState(0.5)
  const [showLesion, setShowLesion] = useState(true)
  const [selected, setSelected] = useState<string | null>(null)
  return <div>
    <div style={{display:'flex', gap:12}}>
      <span style={{padding:'2px 8px', borderRadius:8, background: mode==='real' ? '#d1ffd1' : '#ffe0b2'}}>{mode.toUpperCase()}</span>
      <label>Opacity <input type="range" min="0.1" max="1" step="0.1" value={opacity} onChange={(e)=>setOpacity(Number(e.target.value))} /></label>
      <label><input type="checkbox" checked={showLesion} onChange={(e)=>setShowLesion(e.target.checked)} /> Show lesion</label>
      <button onClick={()=>setSelected(null)}>Reset camera</button>
    </div>
    {selected && <div>Lesion metadata: {selected}</div>}
    <div style={{height:300}}>
      <Canvas camera={{position:[2,2,2]}}>
        <ambientLight />
        <mesh><sphereGeometry args={[1, 32, 32]} /><meshStandardMaterial color={'#6aa8ff'} transparent opacity={opacity} /></mesh>
        {showLesion && <mesh position={[0.4,0.2,0.1]} onClick={()=>setSelected('segment S6')}><sphereGeometry args={[0.2, 16, 16]} /><meshStandardMaterial color={'red'} /></mesh>}
        <OrbitControls />
      </Canvas>
    </div>
    <button>Export screenshot</button>
  </div>
}
