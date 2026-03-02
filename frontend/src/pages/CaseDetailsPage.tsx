import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Viewer3D } from '../components/Viewer3D'
import type { ArtifactItem, StatusPayload } from '../types'

export function CaseDetailsPage() {
  const { id } = useParams()
  const [report, setReport] = useState('')
  const [status, setStatus] = useState<StatusPayload | null>(null)
  const [artifacts, setArtifacts] = useState<ArtifactItem[]>([])
  const [tab, setTab] = useState<'timeline' | 'artifacts' | 'viewer2d' | 'viewer3d'>('timeline')

  const refresh = async () => {
    if (!id) return
    const [s, a, r] = await Promise.all([
      api.get(`/cases/${id}/status`),
      api.get(`/cases/${id}/artifacts`),
      api.get(`/cases/${id}/report`).catch(()=>({data:{reportText:''}}))
    ])
    setStatus(s.data)
    setArtifacts(a.data)
    setReport(r.data.reportText)
  }

  useEffect(() => { refresh().catch(()=>{}) }, [id])
  const start = async () => { await api.post(`/cases/${id}/process`); await refresh() }

  const isReal = artifacts.some(a => a.type === 'LIVER_MASK')

  return <div>
    <h2>Case #{id}</h2>
    <button onClick={start}>Запустить анализ</button>
    <div style={{margin:'12px 0'}}>Mode badge: <b>{isReal ? 'REAL' : 'MOCK'}</b></div>
    <div style={{display:'flex', gap:8}}>
      <button onClick={()=>setTab('timeline')}>Timeline</button>
      <button onClick={()=>setTab('artifacts')}>Artifacts</button>
      <button onClick={()=>setTab('viewer2d')}>2D Viewer</button>
      <button onClick={()=>setTab('viewer3d')}>3D Viewer</button>
    </div>

    {tab === 'timeline' && <ul>{status?.stageAuditTrail?.map((e, idx)=><li key={idx}>{e.at}: {e.action}</li>)}</ul>}
    {tab === 'artifacts' && <ul>{artifacts.map(a=><li key={a.id}><a href={`http://localhost:8080${a.downloadUrl}`} target="_blank">{a.type}: {a.fileName}</a></li>)}</ul>}
    {tab === 'viewer2d' && <p>OHIF integration path enabled (link mode planned), fallback Cornerstone3D to be enabled in next step.</p>}
    {tab === 'viewer3d' && <Viewer3D mode={isReal ? 'real' : 'mock'} />}

    <h3>Report</h3><p>{report}</p>
  </div>
}
