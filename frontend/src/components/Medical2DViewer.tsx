import { Alert, Box, Button, CircularProgress, FormControlLabel, Grid2, MenuItem, Slider, Stack, Switch, TextField, Typography } from '@mui/material'
import { useEffect, useMemo, useRef, useState } from 'react'
import * as nifti from 'nifti-reader-js'
import { authorizedFetch } from '../api/client'
import { ARTIFACT_TYPES, FINDING_TYPES, type ArtifactItem, type FindingItem } from '../types'

type TypedArray = Int8Array | Uint8Array | Int16Array | Int32Array | Float32Array | Float64Array | Uint16Array | Uint32Array
type TypedArrayConstructor<T extends TypedArray> = {
  BYTES_PER_ELEMENT: number
  new(length: number): T
}

function normalizeArrayBuffer(payload: ArrayBuffer | ArrayBufferView): ArrayBuffer {
  if (payload instanceof ArrayBuffer) {
    return payload
  }
  return payload.buffer.slice(payload.byteOffset, payload.byteOffset + payload.byteLength) as ArrayBuffer
}

function readTypedArray<T extends TypedArray>(
  image: ArrayBuffer | ArrayBufferView,
  littleEndian: boolean,
  constructor: TypedArrayConstructor<T>,
  reader: (view: DataView, offset: number, littleEndian: boolean) => number
): T {
  const buffer = normalizeArrayBuffer(image)
  if (littleEndian) {
    return new constructor(buffer.byteLength / constructor.BYTES_PER_ELEMENT).map((_, index) =>
      reader(new DataView(buffer), index * constructor.BYTES_PER_ELEMENT, true)
    ) as T
  }

  const view = new DataView(buffer)
  const typed = new constructor(buffer.byteLength / constructor.BYTES_PER_ELEMENT)
  for (let index = 0; index < typed.length; index += 1) {
    typed[index] = reader(view, index * constructor.BYTES_PER_ELEMENT, false)
  }
  return typed
}

function convertNiftiImage(header: any, image: ArrayBuffer | ArrayBufferView): TypedArray {
  switch (header.datatypeCode) {
    case 2:
      return new Uint8Array(normalizeArrayBuffer(image))
    case 4:
      return readTypedArray(image, header.littleEndian, Int16Array, (view, offset, littleEndian) => view.getInt16(offset, littleEndian))
    case 8:
      return readTypedArray(image, header.littleEndian, Int32Array, (view, offset, littleEndian) => view.getInt32(offset, littleEndian))
    case 16:
      return readTypedArray(image, header.littleEndian, Float32Array, (view, offset, littleEndian) => view.getFloat32(offset, littleEndian))
    case 64:
      return readTypedArray(image, header.littleEndian, Float64Array, (view, offset, littleEndian) => view.getFloat64(offset, littleEndian))
    case 256:
      return new Int8Array(normalizeArrayBuffer(image))
    case 512:
      return readTypedArray(image, header.littleEndian, Uint16Array, (view, offset, littleEndian) => view.getUint16(offset, littleEndian))
    case 768:
      return readTypedArray(image, header.littleEndian, Uint32Array, (view, offset, littleEndian) => view.getUint32(offset, littleEndian))
    default:
      throw new Error(`Unsupported NIfTI datatype code: ${header.datatypeCode}`)
  }
}

async function loadNifti(url: string): Promise<{ header: any; data: TypedArray }> {
  const response = await authorizedFetch(url)
  if (!response.ok) throw new Error(`Failed to load NIfTI: HTTP ${response.status}`)
  const payload = await response.arrayBuffer()
  let data = payload
  if (nifti.isCompressed(data)) data = nifti.decompress(data)
  if (!nifti.isNIFTI(data)) throw new Error('Not a NIfTI artifact')
  const header = nifti.readHeader(data) as any
  const image = nifti.readImage(header, data)
  return { header, data: convertNiftiImage(header, image) }
}

function clampSlice(slice: number, depth: number): number {
  return Math.max(0, Math.min(depth - 1, slice))
}

function sliceIndexForFinding(finding: FindingItem | null, depth: number): number | null {
  if (!finding?.location) {
    return null
  }

  const centroidZ = finding.location.centroid?.[2]
  if (typeof centroidZ === 'number' && Number.isFinite(centroidZ)) {
    return clampSlice(Math.round(centroidZ), depth)
  }

  const minZ = finding.location.bbox?.min?.[2]
  const maxZ = finding.location.bbox?.max?.[2]
  if (typeof minZ === 'number' && typeof maxZ === 'number') {
    return clampSlice(Math.round((minZ + maxZ) / 2), depth)
  }

  return null
}

type NiftiVolume = { width: number; height: number; depth: number; data: TypedArray; sourceType: string }

export function Medical2DViewer({
  artifacts,
  findings,
  selectedFindingId,
  onSelectFinding,
}: {
  artifacts: ArtifactItem[]
  findings: FindingItem[]
  selectedFindingId: number | null
  onSelectFinding?: (findingId: number) => void
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [slice, setSlice] = useState(0)
  const [windowWidth, setWindowWidth] = useState(400)
  const [windowCenter, setWindowCenter] = useState(40)
  const [showLiver, setShowLiver] = useState(true)
  const [showLesion, setShowLesion] = useState(true)
  const [baseType, setBaseType] = useState<typeof ARTIFACT_TYPES.ENHANCED_VOLUME | typeof ARTIFACT_TYPES.ORIGINAL_STUDY>(ARTIFACT_TYPES.ENHANCED_VOLUME)
  const [volume, setVolume] = useState<NiftiVolume | null>(null)
  const [liverMask, setLiverMask] = useState<TypedArray | null>(null)
  const [lesionMask, setLesionMask] = useState<TypedArray | null>(null)

  const byType = useMemo(() => Object.fromEntries(artifacts.map((a) => [a.type, a])), [artifacts])
  const suspiciousZones = useMemo(() => findings.filter((finding) => finding.type === FINDING_TYPES.LESION), [findings])
  const selectedFinding = useMemo(
    () => suspiciousZones.find((finding) => finding.id === selectedFindingId) ?? null,
    [suspiciousZones, selectedFindingId]
  )
  const selectedFindingSlice = useMemo(
    () => (volume && selectedFinding ? sliceIndexForFinding(selectedFinding, volume.depth) : null),
    [selectedFinding, volume]
  )

  useEffect(() => {
    const load = async () => {
      const options = [baseType, ARTIFACT_TYPES.ENHANCED_VOLUME, ARTIFACT_TYPES.ORIGINAL_STUDY] as const
      const chosen = options.map((t) => byType[t]).find(Boolean)
      if (!chosen) {
        setVolume(null)
        setLiverMask(null)
        setLesionMask(null)
        setError(null)
        return
      }
      setLoading(true)
      setError(null)
      try {
        const vol = await loadNifti(chosen.downloadUrl)
        const width = vol.header.dims[1]
        const height = vol.header.dims[2]
        const depth = vol.header.dims[3]
        setVolume({ width, height, depth, data: vol.data, sourceType: chosen.type })
        setSlice(Math.floor(depth / 2))
        setLiverMask(byType[ARTIFACT_TYPES.LIVER_MASK] ? (await loadNifti(byType[ARTIFACT_TYPES.LIVER_MASK].downloadUrl)).data : null)
        setLesionMask(byType[ARTIFACT_TYPES.LESION_MASK] ? (await loadNifti(byType[ARTIFACT_TYPES.LESION_MASK].downloadUrl)).data : null)
      } catch (loadError) {
        setVolume(null)
        const reason = loadError instanceof Error ? loadError.message : 'Unknown viewer error'
        setError(`Unable to render artifacts as NIfTI (${reason}). OHIF/DICOM-native viewer remains pending.`)
      } finally {
        setLoading(false)
      }
    }
    load().catch(() => setError('Viewer load failure'))
  }, [byType, baseType])

  useEffect(() => {
    if (selectedFindingSlice === null) return
    setSlice((currentSlice) => (currentSlice === selectedFindingSlice ? currentSlice : selectedFindingSlice))
  }, [selectedFindingSlice])

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

  const focusFinding = (finding: FindingItem) => {
    onSelectFinding?.(finding.id)
    const targetSlice = sliceIndexForFinding(finding, volume.depth)
    if (targetSlice !== null) {
      setSlice(targetSlice)
    }
  }

  return <Stack spacing={2}>
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
      <TextField select size="small" label="Base volume" value={baseType} onChange={(e) => setBaseType(e.target.value as typeof baseType)} sx={{ minWidth: 220 }}>
        <MenuItem value={ARTIFACT_TYPES.ENHANCED_VOLUME}>Enhanced volume</MenuItem>
        <MenuItem value={ARTIFACT_TYPES.ORIGINAL_STUDY}>Original study</MenuItem>
      </TextField>
      <Alert severity="info" sx={{ py: 0 }}>Verified: NIfTI artifact-backed rendering · Pending: OHIF/DICOM-native workflow</Alert>
    </Stack>
    <Grid2 container spacing={2}>
      <Grid2 size={{ xs: 12, lg: 8 }}>
        <Box
          component="canvas"
          ref={canvasRef}
          data-testid="viewer-2d-canvas"
          sx={{ width: '100%', borderRadius: 2, border: '1px solid', borderColor: 'divider', imageRendering: 'pixelated', bgcolor: '#0f172a' }}
        />
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
          {selectedFinding && (
            <Alert severity="info" data-testid="viewer-2d-selected-finding">
              Focused finding: {selectedFinding.label}
              {selectedFindingSlice !== null ? ` · slice ${selectedFindingSlice + 1}/${volume.depth}` : ' · spatial slice hint unavailable'}
            </Alert>
          )}
          {suspiciousZones.length > 0 && (
            <Stack spacing={1}>
              <Typography variant="subtitle2">Finding-to-slice navigation</Typography>
              {suspiciousZones.map((finding) => {
                const focusSlice = sliceIndexForFinding(finding, volume.depth)
                return (
                  <Stack
                    key={finding.id}
                    direction={{ xs: 'column', sm: 'row' }}
                    spacing={1}
                    alignItems={{ xs: 'stretch', sm: 'center' }}
                  >
                    <Typography variant="body2" sx={{ flex: 1 }}>
                      {finding.label}
                      {focusSlice !== null ? ` · slice ${focusSlice + 1}` : ' · no slice hint'}
                    </Typography>
                    <Button
                      size="small"
                      variant={selectedFindingId === finding.id ? 'contained' : 'outlined'}
                      onClick={() => focusFinding(finding)}
                      data-testid={`viewer-2d-focus-finding-${finding.id}`}
                    >
                      Focus slice
                    </Button>
                  </Stack>
                )
              })}
            </Stack>
          )}
        </Stack>
      </Grid2>
    </Grid2>
  </Stack>
}
