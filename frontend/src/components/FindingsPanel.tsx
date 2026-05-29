import { Alert, Box, Chip, LinearProgress, Stack, Typography } from '@mui/material'
import { FINDING_TYPES, type FindingItem } from '../types'

type ConfidenceLevel = 'high' | 'medium' | 'low' | 'unknown'

function confidenceLevel(confidence: number | null): ConfidenceLevel {
  if (confidence === null || !Number.isFinite(confidence)) return 'unknown'
  if (confidence >= 0.7) return 'high'
  if (confidence >= 0.4) return 'medium'
  return 'low'
}

const LEVEL_COLOR: Record<ConfidenceLevel, 'error' | 'warning' | 'info' | 'inherit'> = {
  high: 'error',
  medium: 'warning',
  low: 'info',
  unknown: 'inherit',
}

function confidenceText(confidence: number | null): string {
  return confidence === null || !Number.isFinite(confidence) ? 'n/a' : `${Math.round(confidence * 100)}%`
}

/**
 * Interactive findings list with per-finding confidence bars. Selecting a row
 * drives the shared selectedFindingId (which the 2D/3D viewers already react
 * to); `onLocate` optionally surfaces a "view in imaging" affordance.
 */
export function FindingsPanel({
  findings,
  selectedFindingId,
  onSelect,
  onLocate,
  emptyMessage = 'No structured findings for this case.',
}: {
  findings: FindingItem[]
  selectedFindingId: number | null
  onSelect?: (id: number) => void
  onLocate?: (id: number) => void
  emptyMessage?: string
}) {
  const lesions = findings.filter((f) => f.type === FINDING_TYPES.LESION)
  if (lesions.length === 0) {
    return <Alert severity="info">{emptyMessage}</Alert>
  }

  return (
    <Stack spacing={1.2}>
      {lesions.map((finding) => {
        const level = confidenceLevel(finding.confidence)
        const color = LEVEL_COLOR[level]
        const selected = finding.id === selectedFindingId
        const pct = finding.confidence !== null && Number.isFinite(finding.confidence)
          ? Math.max(0, Math.min(100, finding.confidence * 100))
          : 0
        return (
          <Box
            key={finding.id}
            onClick={() => onSelect?.(finding.id)}
            data-testid={`finding-row-${finding.id}`}
            sx={{
              p: 1.4,
              borderRadius: 2,
              border: '1px solid',
              borderColor: selected ? 'primary.main' : 'divider',
              bgcolor: selected ? 'action.selected' : 'background.paper',
              cursor: onSelect ? 'pointer' : 'default',
              transition: 'border-color .15s, background-color .15s',
              '&:hover': onSelect ? { borderColor: 'primary.light' } : undefined,
            }}
          >
            <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
              <Typography variant="body2" sx={{ fontWeight: 700 }}>{finding.label}</Typography>
              <Stack direction="row" spacing={0.5} alignItems="center">
                {finding.location?.segment && <Chip size="small" variant="outlined" label={`Segment ${finding.location.segment}`} />}
                {finding.location?.suspicion && <Chip size="small" color="warning" label={finding.location.suspicion} />}
              </Stack>
            </Stack>

            <Stack direction="row" alignItems="center" spacing={1} sx={{ mt: 0.8 }}>
              <Typography variant="caption" color="text.secondary" sx={{ width: 78 }}>
                Confidence
              </Typography>
              <LinearProgress
                variant="determinate"
                value={pct}
                color={color === 'inherit' ? 'inherit' : color}
                sx={{ flex: 1, height: 8, borderRadius: 4 }}
              />
              <Typography variant="caption" sx={{ width: 38, textAlign: 'right', fontWeight: 700 }}>
                {confidenceText(finding.confidence)}
              </Typography>
            </Stack>

            <Stack direction="row" spacing={2} sx={{ mt: 0.6 }} flexWrap="wrap" useFlexGap>
              <Typography variant="caption" color="text.secondary">
                Volume {finding.volumeMm3 != null ? `${finding.volumeMm3} mm³` : 'n/a'}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Size {finding.sizeMm != null ? `${finding.sizeMm} mm` : 'n/a'}
              </Typography>
              {onLocate && (
                <Typography
                  variant="caption"
                  color="primary"
                  data-testid={`finding-locate-${finding.id}`}
                  sx={{ cursor: 'pointer', fontWeight: 700, ml: 'auto' }}
                  onClick={(e) => { e.stopPropagation(); onLocate(finding.id) }}
                >
                  View in 2D →
                </Typography>
              )}
            </Stack>
          </Box>
        )
      })}
    </Stack>
  )
}
