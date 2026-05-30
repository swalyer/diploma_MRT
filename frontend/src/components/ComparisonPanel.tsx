import { Alert, Box, Card, CardContent, Chip, Grid2, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { CaseItem, ComparisonResponse, StudySummary } from '../types'

function DeltaChip({ label, value, unit, pct }: { label: string; value: number; unit?: string; pct?: number | null }) {
  const arrow = value > 0 ? '▲' : value < 0 ? '▼' : '='
  // Growth in lesion burden is the clinically notable direction → warn on increase.
  const color = value > 0 ? 'warning' : value < 0 ? 'success' : 'default'
  const sign = value > 0 ? '+' : ''
  const pctText = pct === null || pct === undefined ? '' : ` (${pct > 0 ? '+' : ''}${pct}%)`
  return (
    <Chip
      color={color}
      variant={value === 0 ? 'outlined' : 'filled'}
      label={`${label}: ${arrow} ${sign}${value}${unit ? ' ' + unit : ''}${pctText}`}
    />
  )
}

function SummaryCard({ title, summary }: { title: string; summary: StudySummary }) {
  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="overline" color="text.secondary">{title}</Typography>
        <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>Case #{summary.caseId} · {summary.modality}</Typography>
        <Typography variant="caption" color="text.secondary">{new Date(summary.createdAt).toLocaleString()}</Typography>
        <Stack mt={1.2} spacing={0.5}>
          <Typography variant="body2">Lesions: <b>{summary.lesionCount}</b></Typography>
          <Typography variant="body2">Total volume: <b>{summary.totalVolumeMm3} mm³</b></Typography>
          <Typography variant="body2">Largest lesion: <b>{summary.largestLesionMm} mm</b></Typography>
        </Stack>
      </CardContent>
    </Card>
  )
}

export function ComparisonPanel({ caseId, resultReady }: { caseId: number; resultReady: boolean }) {
  const [candidates, setCandidates] = useState<CaseItem[]>([])
  const [baselineId, setBaselineId] = useState<number | ''>('')
  const [comparison, setComparison] = useState<ComparisonResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!resultReady) return
    let active = true
    api.get(`/cases/${caseId}/comparison/candidates`)
      .then((res) => { if (active) setCandidates(res.data) })
      .catch(() => { if (active) setError('Could not load comparable studies.') })
    return () => { active = false }
  }, [caseId, resultReady])

  useEffect(() => {
    if (baselineId === '') { setComparison(null); return }
    let active = true
    setError(null)
    api.get(`/cases/${caseId}/comparison`, { params: { against: baselineId } })
      .then((res) => { if (active) setComparison(res.data) })
      .catch(() => { if (active) setError('Comparison failed.') })
    return () => { active = false }
  }, [caseId, baselineId])

  if (!resultReady) {
    return <Alert severity="info">Longitudinal comparison becomes available once this study has completed processing.</Alert>
  }

  return (
    <Stack spacing={2}>
      <Alert severity="info">
        Aggregate lesion-burden comparison across studies of the same patient. This is decision-support context, not
        registered lesion-to-lesion matching.
      </Alert>

      {candidates.length === 0 ? (
        <Alert severity="info" data-testid="comparison-no-candidates">
          No other completed studies for this patient to compare against yet.
        </Alert>
      ) : (
        <TextField
          select
          size="small"
          label="Compare against earlier study"
          value={baselineId}
          onChange={(e) => setBaselineId(e.target.value === '' ? '' : Number(e.target.value))}
          sx={{ maxWidth: 420 }}
          data-testid="comparison-baseline-select"
        >
          <MenuItem value="">— select baseline —</MenuItem>
          {candidates.map((c) => (
            <MenuItem key={c.id} value={c.id}>
              Case #{c.id} · {c.modality} · {new Date(c.createdAt).toLocaleDateString()}
            </MenuItem>
          ))}
        </TextField>
      )}

      {error && <Alert severity="warning">{error}</Alert>}

      {comparison && (
        <Box data-testid="comparison-result">
          <Grid2 container spacing={2}>
            <Grid2 size={{ xs: 12, md: 6 }}><SummaryCard title="Baseline" summary={comparison.baseline} /></Grid2>
            <Grid2 size={{ xs: 12, md: 6 }}><SummaryCard title="Follow-up (this study)" summary={comparison.followup} /></Grid2>
          </Grid2>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 2 }}>
            <DeltaChip label="Lesions" value={comparison.delta.lesionCountDelta} />
            <DeltaChip label="Total volume" value={comparison.delta.totalVolumeDeltaMm3} unit="mm³" pct={comparison.delta.totalVolumePctChange} />
            <DeltaChip label="Largest lesion" value={comparison.delta.largestLesionDeltaMm} unit="mm" />
          </Stack>
        </Box>
      )}
    </Stack>
  )
}
