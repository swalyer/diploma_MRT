import { Alert, Button, Card, CardContent, LinearProgress, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'

export function CreateCasePage() {
  const [patientPseudoId, setPatientPseudoId] = useState('PSEUDO-001')
  const [modality, setModality] = useState('CT')
  const [file, setFile] = useState<File | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const nav = useNavigate()

  const submit = async () => {
    setError(null)
    if (!file) return setError('Please select a DICOM ZIP or NIfTI file before submitting.')
    setLoading(true)
    try {
      const created = await api.post('/cases', { patientPseudoId, modality })
      const fd = new FormData(); fd.append('file', file)
      await api.post(`/cases/${created.data.id}/upload`, fd)
      nav(`/cases/${created.data.id}`)
    } catch {
      setError('Create/upload failed. Ensure API is reachable and file is valid.')
    } finally {
      setLoading(false)
    }
  }

  return <Card><CardContent>
    <Stack spacing={2}>
      <Typography variant="h4" fontWeight={700}>Create case</Typography>
      <TextField label="Patient pseudo ID" value={patientPseudoId} onChange={(e)=>setPatientPseudoId(e.target.value)} />
      <TextField select label="Declared modality" value={modality} onChange={(e)=>setModality(e.target.value)}>
        <MenuItem value="CT">CT</MenuItem><MenuItem value="MRI">MRI (experimental)</MenuItem>
      </TextField>
      <Button variant="outlined" component="label">Choose study file<input hidden type="file" onChange={(e)=>setFile(e.target.files?.[0] ?? null)} /></Button>
      <Typography variant="body2" color="text.secondary">Supported: NIfTI (`.nii`, `.nii.gz`) or DICOM package (`.zip`).</Typography>
      {file && <Alert severity="info">Selected: {file.name}</Alert>}
      {error && <Alert severity="error">{error}</Alert>}
      {loading && <LinearProgress />}
      <Button variant="contained" onClick={submit} disabled={loading}>Create & upload</Button>
    </Stack>
  </CardContent></Card>
}
