import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api/client'
import { Viewer3D } from '../components/Viewer3D'

export function CaseDetailsPage() {
  const { id } = useParams()
  const [report, setReport] = useState('')
  useEffect(() => { if (id) { api.get(`/cases/${id}/report`).then((r)=>setReport(r.data.reportText)).catch(()=>{}) } }, [id])
  const start = async () => { await api.post(`/cases/${id}/process`) }
  return <div><h2>Case #{id}</h2><button onClick={start}>Запустить анализ</button><h3>Overview</h3><p>Pipeline timeline: upload → preprocessing → segmentation → report</p><h3>2D Viewer</h3><p>Slice scroll and overlay placeholders for original/enhanced/liver/lesion.</p><h3>Report</h3><p>{report}</p><h3>3D Viewer</h3><Viewer3D /></div>
}
