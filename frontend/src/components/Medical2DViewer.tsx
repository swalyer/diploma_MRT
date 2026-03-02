import { Alert, Box, CircularProgress, FormControlLabel, Grid2, MenuItem, Slider, Stack, Switch, TextField, Typography } from '@mui/material'
import { useEffect, useMemo, useRef, useState } from 'react'
import * as nifti from 'nifti-reader-js'
import type { ArtifactItem } from '../types'
import { useAuthStore } from '../store/authStore'

type TypedArray = Uint8Array | Int16Array | Int32Array | Float32Array | Float64Array | Uint16Array | Uint32Array

type NiftiVolume = { width: number; height: number; depth: number; data: TypedArray; sourceType: string }

async function loadNifti(url: string, token: string | null): Promise<{ header: any; data: TypedArray }> {
  const response = await fetch(`http://localhost:8080${url}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  const payload = await response.arrayBuffer()
  let data = payload
  if (nifti.isCompressed(data)) data = nifti.decompress(data)
  if (!nifti.isNIFTI(data)) throw new Error('Not a NIfTI artifact')
  const header = nifti.readHeader(data) as any
  const image = nifti.readImage(header, data)
  return { header, data: nifti.Utils.convertToTypedArray(header, image) as TypedArray }
}

export function Medical2DViewer({ artifacts }: { artifacts: ArtifactItem[] }) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [slice, setSlice] = useState(0)
  const [windowWidth, setWindowWidth] = useState(400)
  const [windowCenter, setWindowCenter] = useState(40)
  const [showLiver, setShowLiver] = useState(true)
  const [showLesion, setShowLesion] = useState(true)
  const [baseType, setBaseType] = useState<'ENHANCED_VOLUME' | 'NORMALIZED_VOLUME' | 'ORIGINAL_STUDY'>('ENHANCED_VOLUME')
  const [volume, setVolume] = useState<NiftiVolume | null>(null)
  const [liverMask, setLiverMask] = useState<TypedArray | null>(null)
  const [lesionMask, setLesionMask] = useState<TypedArray | null>(null)

  const byType = useMemo(() => Object.fromEntries(artifacts.map((a) => [a.type, a])), [artifacts])
  const token = useAuthStore((s) => s.token)

  useEffect(() => {
    const load = async () => {
      const options = [baseType, 'ENHANCED_VOLUME', 'NORMALIZED_VOLUME', 'ORIGINAL_STUDY'] as const
      const chosen = options.map((t) => byType[t]).find(Boolean)
      if (!chosen) {
        setVolume(null)
        return
      }
      setLoading(true)
      setError(null)
      try {
        const vol = await loadNifti(chosen.downloadUrl, token)
        const width = vol.header.dims[1]
        const height = vol.header.dims[2]
        const depth = vol.header.dims[3]
        setVolume({ width, height, depth, data: vol.data, sourceType: chosen.type })
        setSlice(Math.floor(depth / 2))
        setLiverMask(byType.LIVER_MASK ? (await loadNifti(byType.LIVER_MASK.downloadUrl, token)).data : null)
        setLesionMask(byType.LESION_MASK ? (await loadNifti(byType.LESION_MASK.downloadUrl, token)).data : null)
      } catch {
        setError('Unable to render artifacts as NIfTI. OHIF/DICOM-native viewer remains pending.')
      } finally {
        setLoading(false)
      }
    }
    load().catch(() => setError('Viewer load failure'))
  }, [byType, token, baseType])

  useEffect(() => {
    if (!volume || !canvasRef.current) return
    const { width, height, data } = volume
    const canvas = canvasRef.current
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const imageData = ctx.createImageData(width, height)
    const offset = slice * width * height
    for (let i = 0; i < width * height; i++) {
      const value = Number(data[offset + i] ?? 0)
      const min = windowCenter - windowWidth / 2
      const max = windowCenter + windowWidth / 2
      const normalized = Math.max(0, Math.min(255, ((value - min) / (max - min)) * 255))
      imageData.data[i * 4] = normalized
      imageData.data[i * 4 + 1] = normalized
      imageData.data[i * 4 + 2] = normalized
      imageData.data[i * 4 + 3] = 255

      const liver = showLiver && liverMask ? Number(liverMask[offset + i]) > 0 : false
      const lesion = showLesion && lesionMask ? Number(lesionMask[offset + i]) > 0 : false
      if (liver) {
        imageData.data[i * 4] = 35
        imageData.data[i * 4 + 1] = 182
        imageData.data[i * 4 + 2] = 138
      }
      if (lesion) {
        imageData.data[i * 4] = 242
        imageData.data[i * 4 + 1] = 65
        imageData.data[i * 4 + 2] = 76
      }
    }
    ctx.putImageData(imageData, 0, 0)
  }, [volume, slice, windowCenter, windowWidth, liverMask, lesionMask, showLiver, showLesion])

  if (loading) return <CircularProgress />
  if (error) return <Alert severity="warning">{error}</Alert>
  if (!volume) return <Alert severity="info">No NIfTI-compatible volume artifacts available yet.</Alert>

  return <Stack spacing={2}>
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
      <TextField select size="small" label="Base volume" value={baseType} onChange={(e) => setBaseType(e.target.value as any)} sx={{ minWidth: 220 }}>
        <MenuItem value="ENHANCED_VOLUME">Enhanced volume</MenuItem>
        <MenuItem value="NORMALIZED_VOLUME">Normalized volume</MenuItem>
        <MenuItem value="ORIGINAL_STUDY">Original study</MenuItem>
      </TextField>
      <Alert severity="info" sx={{ py: 0 }}>Verified: NIfTI artifact-backed rendering · Pending: OHIF/DICOM-native workflow</Alert>
    </Stack>
    <Grid2 container spacing={2}>
      <Grid2 size={{ xs: 12, lg: 8 }}>
        <Box component="canvas" ref={canvasRef} sx={{ width: '100%', borderRadius: 2, border: '1px solid #d4dce8', imageRendering: 'pixelated', bgcolor: '#0f172a' }} />
      </Grid2>
      <Grid2 size={{ xs: 12, lg: 4 }}>
        <Stack spacing={1.2} sx={{ minWidth: 300 }}>
          <Typography variant="subtitle2">Slice {slice + 1}/{volume.depth}</Typography>
          <Slider min={0} max={volume.depth - 1} value={slice} onChange={(_, v) => setSlice(Number(v))} />
          <Typography variant="subtitle2">Window width</Typography>
          <Slider min={50} max={1500} value={windowWidth} onChange={(_, v) => setWindowWidth(Number(v))} />
          <Typography variant="subtitle2">Window center</Typography>
          <Slider min={-400} max={400} value={windowCenter} onChange={(_, v) => setWindowCenter(Number(v))} />
          <FormControlLabel control={<Switch checked={showLiver} onChange={(_, c) => setShowLiver(c)} />} label="Liver mask overlay" />
          <FormControlLabel control={<Switch checked={showLesion} onChange={(_, c) => setShowLesion(c)} />} label="Lesion mask overlay" />
        </Stack>
      </Grid2>
    </Grid2>
  </Stack>
}
