import { useState } from 'react'
import { api } from '../api/client'

export function CreateCasePage() {
  const [patientPseudoId, setPatientPseudoId] = useState('PSEUDO-001')
  const [modality, setModality] = useState('MRI')
  const [file, setFile] = useState<File | null>(null)
  const submit = async () => {
    const created = await api.post('/cases', { patientPseudoId, modality })
    if (file) {
      const fd = new FormData(); fd.append('file', file)
      await api.post(`/cases/${created.data.id}/upload`, fd)
    }
  }
  return <div><h2>Create Case</h2><input value={patientPseudoId} onChange={(e)=>setPatientPseudoId(e.target.value)} /><select value={modality} onChange={(e)=>setModality(e.target.value)}><option>MRI</option><option>CT</option></select><input type="file" onChange={(e)=>setFile(e.target.files?.[0] ?? null)} /><button onClick={submit}>Create & Upload</button></div>
}
