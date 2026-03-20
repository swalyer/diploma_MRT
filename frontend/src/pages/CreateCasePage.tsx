import { Alert, Box, Button, Card, CardContent, Chip, Grid2, LinearProgress, MenuItem, Stack, TextField, Typography } from '@mui/material'
import type { AxiosError } from 'axios'
import { DragEvent, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'

type ArtifactUploadResponse = {
  id: number
  type: string
  mimeType: string
  fileName: string
  downloadUrl: string
}

export function CreateCasePage() {
  const [patientPseudoId, setPatientPseudoId] = useState('PSEUDO-001')
  const [modality, setModality] = useState('CT')
  const [file, setFile] = useState<File | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const nav = useNavigate()

  const validationError = useMemo(() => {
    if (!file) return null
    if (!/\.(nii|nii\.gz)$/i.test(file.name)) return 'Unsupported format. Allowed: .nii, .nii.gz'
    return null
  }, [file])

  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    const next = e.dataTransfer.files?.[0]
    if (next) setFile(next)
  }

  const describeAxiosError = (prefix: string, err: unknown) => {
    const axiosError = err as AxiosError<{ error?: string; message?: string }>
    const status = axiosError.response?.status
    const message = axiosError.response?.data?.message || axiosError.response?.data?.error
    return `${prefix}${status ? ` (HTTP ${status})` : ''}${message ? `: ${message}` : '.'}`
  }

  const submit = async () => {
    setError(null)
    setSuccessMessage(null)
    if (!patientPseudoId.trim()) return setError('Patient pseudo ID is required.')
    if (!file) return setError('Please attach a NIfTI file to proceed.')
    if (validationError) return setError(validationError)
    setLoading(true)

    let caseId: number
    try {
      const created = await api.post('/cases', { patientPseudoId, modality })
      caseId = Number(created.data?.id)
      if (!caseId) {
        throw new Error('Case created but response does not include case id')
      }
      setSuccessMessage(`Case #${caseId} created.`)
    } catch (err) {
      setError(describeAxiosError('Case creation failed', err))
      setLoading(false)
      return
    }

    try {
      const fd = new FormData()
      fd.append('file', file)
      const uploadRes = await api.post<ArtifactUploadResponse>(`/cases/${caseId}/upload`, fd)
      const payload = uploadRes.data
      const isValidResponse = Boolean(payload?.id && payload?.type && payload?.mimeType && payload?.fileName && payload?.downloadUrl)
      if (!isValidResponse) {
        throw new Error('Upload completed but response payload is invalid')
      }
      setSuccessMessage(`Case #${caseId} created and source artifact uploaded (${payload.fileName}).`)
    } catch (err) {
      setError(describeAxiosError(`Case #${caseId} created, but upload failed`, err))
      setLoading(false)
      return
    }

    try {
      await api.get(`/cases/${caseId}`)
      nav(`/cases/${caseId}`)
    } catch (err) {
      setError(describeAxiosError(`Case #${caseId} created and upload succeeded, but post-upload refresh failed`, err))
      setLoading(false)
      return
    }

    setLoading(false)
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

          <TextField label="Patient pseudo ID" value={patientPseudoId} onChange={(e) => setPatientPseudoId(e.target.value)} inputProps={{ 'data-testid': 'create-case-pseudo-id' }} />
          <TextField select label="Declared modality" value={modality} onChange={(e) => setModality(e.target.value)} SelectProps={{ inputProps: { 'data-testid': 'create-case-modality' } }}>
            <MenuItem value="CT">CT (primary path)</MenuItem>
            <MenuItem value="MRI">MRI (heuristic-supported)</MenuItem>
          </TextField>

          <Box onDrop={onDrop} onDragOver={(e) => e.preventDefault()} sx={{ p: 3, border: '2px dashed #aac1ec', borderRadius: 2, background: '#f8fbff' }}>
            <Typography fontWeight={700}>Drag and drop study package</Typography>
            <Typography variant="body2" color="text.secondary">Accepted: `.nii`, `.nii.gz`. DICOM/ZIP ingestion is disabled until converter runtime is implemented.</Typography>
            <Button sx={{ mt: 1.5 }} variant="outlined" component="label">Choose file<input data-testid="study-file-input" hidden type="file" accept=".nii,.nii.gz" onChange={(e) => setFile(e.target.files?.[0] ?? null)} /></Button>
          </Box>

          {file && <Alert severity={validationError ? 'error' : 'info'}>Selected: {file.name}{validationError ? ` · ${validationError}` : ''}</Alert>}
          {successMessage && <Alert severity="success">{successMessage}</Alert>}
          {error && <Alert severity="error">{error}</Alert>}
          {loading && <LinearProgress />}
          <Button variant="contained" onClick={submit} disabled={loading} data-testid="create-case-submit">Create case & upload</Button>
        </Stack>
      </CardContent></Card>
    </Grid2>
    <Grid2 size={{ xs: 12, lg: 4 }}>
      <Card sx={{ height: '100%' }}><CardContent>
        <Typography variant="h6">Upload guidance</Typography>
        <Stack spacing={1} mt={1}>
          <Typography variant="body2">• NIfTI should be volumetric 3D study.</Typography>
          <Typography variant="body2">• MRI lesion support is available via heuristic fallback when dedicated weights are absent.</Typography>
          <Typography variant="body2">• After upload, pipeline run is manual from case page.</Typography>
        </Stack>
      </CardContent></Card>
    </Grid2>
  </Grid2>
}
