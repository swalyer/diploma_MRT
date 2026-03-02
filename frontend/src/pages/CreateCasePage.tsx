import { Alert, Box, Button, Card, CardContent, Chip, Grid2, LinearProgress, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { DragEvent, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'

export function CreateCasePage() {
  const [patientPseudoId, setPatientPseudoId] = useState('PSEUDO-001')
  const [modality, setModality] = useState('CT')
  const [file, setFile] = useState<File | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const nav = useNavigate()

  const validationError = useMemo(() => {
    if (!file) return null
    if (!/\.(nii|nii\.gz|zip)$/i.test(file.name)) return 'Unsupported format. Allowed: .nii, .nii.gz, .zip'
    return null
  }, [file])

  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    const next = e.dataTransfer.files?.[0]
    if (next) setFile(next)
  }

  const submit = async () => {
    setError(null)
    if (!patientPseudoId.trim()) return setError('Patient pseudo ID is required.')
    if (!file) return setError('Please attach a DICOM ZIP or NIfTI file to proceed.')
    if (validationError) return setError(validationError)
    setLoading(true)
    try {
      const created = await api.post('/cases', { patientPseudoId, modality })
      const fd = new FormData()
      fd.append('file', file)
      await api.post(`/cases/${created.data.id}/upload`, fd)
      nav(`/cases/${created.data.id}`)
    } catch {
      setError('Create/upload failed. Check token, artifact format, and backend availability.')
    } finally {
      setLoading(false)
    }
  }

  return <Grid2 container spacing={2}>
    <Grid2 size={{ xs: 12, lg: 8 }}>
      <Card><CardContent>
        <Stack spacing={2.5}>
          <Typography variant="h4">Case Intake Wizard</Typography>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <Chip label="1. Metadata" color="primary" />
            <Chip label="2. Upload source artifact" color="primary" variant="outlined" />
            <Chip label="3. Open case and run pipeline" variant="outlined" />
          </Stack>

          <TextField label="Patient pseudo ID" value={patientPseudoId} onChange={(e) => setPatientPseudoId(e.target.value)} />
          <TextField select label="Declared modality" value={modality} onChange={(e) => setModality(e.target.value)}>
            <MenuItem value="CT">CT (primary path)</MenuItem>
            <MenuItem value="MRI">MRI (experimental)</MenuItem>
          </TextField>

          <Box onDrop={onDrop} onDragOver={(e) => e.preventDefault()} sx={{ p: 3, border: '2px dashed #aac1ec', borderRadius: 2, background: '#f8fbff' }}>
            <Typography fontWeight={700}>Drag and drop study package</Typography>
            <Typography variant="body2" color="text.secondary">Accepted: `.nii`, `.nii.gz`, `.zip` (DICOM package). Upload is persisted by backend storage service.</Typography>
            <Button sx={{ mt: 1.5 }} variant="outlined" component="label">Choose file<input hidden type="file" onChange={(e) => setFile(e.target.files?.[0] ?? null)} /></Button>
          </Box>

          {file && <Alert severity={validationError ? 'error' : 'info'}>Selected: {file.name}{validationError ? ` · ${validationError}` : ''}</Alert>}
          {error && <Alert severity="error">{error}</Alert>}
          {loading && <LinearProgress />}
          <Button variant="contained" onClick={submit} disabled={loading}>Create case & upload</Button>
        </Stack>
      </CardContent></Card>
    </Grid2>
    <Grid2 size={{ xs: 12, lg: 4 }}>
      <Card sx={{ height: '100%' }}><CardContent>
        <Typography variant="h6">Upload guidance</Typography>
        <Stack spacing={1} mt={1}>
          <Typography variant="body2">• ZIP should contain a coherent DICOM series.</Typography>
          <Typography variant="body2">• NIfTI should be volumetric 3D study.</Typography>
          <Typography variant="body2">• MRI lesion support remains experimental.</Typography>
          <Typography variant="body2">• After upload, pipeline run is manual from case page.</Typography>
        </Stack>
      </CardContent></Card>
    </Grid2>
  </Grid2>
}
