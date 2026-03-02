import { Alert, Box, Button, Card, CardContent, Chip, LinearProgress, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { DragEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'

export function CreateCasePage() {
  const [patientPseudoId, setPatientPseudoId] = useState('PSEUDO-001')
  const [modality, setModality] = useState('CT')
  const [file, setFile] = useState<File | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const nav = useNavigate()

  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    const next = e.dataTransfer.files?.[0]
    if (next) setFile(next)
  }

  const submit = async () => {
    setError(null)
    if (!file) return setError('Please attach a DICOM ZIP or NIfTI file to proceed.')
    if (!/\.(nii|nii\.gz|zip)$/i.test(file.name)) return setError('Unsupported format. Allowed: .nii, .nii.gz, .zip')
    setLoading(true)
    try {
      const created = await api.post('/cases', { patientPseudoId, modality })
      const fd = new FormData(); fd.append('file', file)
      await api.post(`/cases/${created.data.id}/upload`, fd)
      nav(`/cases/${created.data.id}`)
    } catch {
      setError('Create/upload failed. Check file integrity and backend availability.')
    } finally {
      setLoading(false)
    }
  }

  return <Card><CardContent>
    <Stack spacing={2.5}>
      <Typography variant="h4">Case intake</Typography>
      <Stack direction="row" spacing={1}><Chip label="Step 1: metadata" color="primary" /><Chip label="Step 2: artifact upload" /><Chip label="Step 3: run pipeline" /></Stack>
      <TextField label="Patient pseudo ID" value={patientPseudoId} onChange={(e)=>setPatientPseudoId(e.target.value)} />
      <TextField select label="Declared modality" value={modality} onChange={(e)=>setModality(e.target.value)}>
        <MenuItem value="CT">CT (real pipeline priority)</MenuItem>
        <MenuItem value="MRI">MRI (experimental)</MenuItem>
      </TextField>

      <Box onDrop={onDrop} onDragOver={(e)=>e.preventDefault()} sx={{ p: 3, border: '1px dashed #aac1ec', borderRadius: 2, background: '#f8fbff' }}>
        <Typography fontWeight={600}>Drag and drop study package</Typography>
        <Typography variant="body2" color="text.secondary">Supported formats: `.nii`, `.nii.gz`, `.zip` (DICOM package). File is uploaded to secure backend storage; local paths are not exposed in public API.</Typography>
        <Button sx={{ mt: 1.5 }} variant="outlined" component="label">Choose file<input hidden type="file" onChange={(e)=>setFile(e.target.files?.[0] ?? null)} /></Button>
      </Box>

      {file && <Alert severity="info">Selected: {file.name}</Alert>}
      {error && <Alert severity="error">{error}</Alert>}
      {loading && <LinearProgress />}
      <Button variant="contained" onClick={submit} disabled={loading}>Create case & upload artifact</Button>
    </Stack>
  </CardContent></Card>
}
