export const ARTIFACT_TYPES = {
  ORIGINAL_STUDY: 'ORIGINAL_STUDY',
  ENHANCED: 'ENHANCED',
  ENHANCED_VOLUME: 'ENHANCED_VOLUME',
  LIVER_MASK: 'LIVER_MASK',
  LESION_MASK: 'LESION_MASK',
  LIVER_MESH: 'LIVER_MESH',
  LESION_MESH: 'LESION_MESH'
} as const

export type ArtifactType = (typeof ARTIFACT_TYPES)[keyof typeof ARTIFACT_TYPES]

export type ExecutionMode = 'mock' | 'real'
export type CaseOrigin = 'LIVE_PROCESSED' | 'SEEDED_DEMO'
export type CaseResultSource = 'NONE' | 'ML_INFERENCE' | 'SEEDED_IMPORT'
export type DemoCategory = 'NORMAL' | 'SINGLE_LESION' | 'MULTIFOCAL' | 'DIFFICULT'

export const FINDING_TYPES = {
  LESION: 'LESION'
} as const

export type FindingType = (typeof FINDING_TYPES)[keyof typeof FINDING_TYPES]

export const AUDIT_ACTIONS = {
  CASE_CREATED: 'CASE_CREATED',
  CASE_UPLOADED: 'CASE_UPLOADED',
  DEMO_CASE_IMPORTED: 'DEMO_CASE_IMPORTED',
  DEMO_CASE_UPDATED: 'DEMO_CASE_UPDATED',
  INFERENCE_ENQUEUED: 'INFERENCE_ENQUEUED',
  INFERENCE_STARTED: 'INFERENCE_STARTED',
  INFERENCE_REQUEST_SENT: 'INFERENCE_REQUEST_SENT',
  INFERENCE_COMPLETED: 'INFERENCE_COMPLETED',
  INFERENCE_FAILED: 'INFERENCE_FAILED',
  INFERENCE_RECOVERED_AFTER_RESTART: 'INFERENCE_RECOVERED_AFTER_RESTART'
} as const

export type AuditAction = (typeof AUDIT_ACTIONS)[keyof typeof AUDIT_ACTIONS]

export type BoundingBox = {
  min: number[]
  max: number[]
}

export type FindingLocation = {
  segment?: string | null
  centroid?: number[] | null
  bbox?: BoundingBox | null
  extent?: number[] | null
  suspicion?: string | null
}

export type MlMetrics = {
  mode: ExecutionMode
  liverModel?: boolean | null
  lesionModel?: boolean | null
  medsamAvailable?: boolean | null
  supportsMri3dSuspiciousZone?: boolean | null
}

export type ProcessDetails = {
  stage?: string | null
  message?: string | null
  error?: string | null
  httpStatus?: number | null
  mlStatus?: string | null
  modelVersion?: string | null
  metrics?: MlMetrics | null
}

export type ReportSections = {
  findings: string
  impression: string
  limitations: string
  recommendation: string
}

export type ReportCapabilities = {
  supports3dLiver: boolean
  supports3dLesion: boolean
}

export type ReportData = {
  modality: 'CT' | 'MRI'
  executionMode: ExecutionMode | null
  lesionCount: number
  evidenceBound: boolean
  sections: ReportSections
  capabilities: ReportCapabilities
}

export type CaseItem = {
  id: number
  patientPseudoId: string
  modality: 'CT' | 'MRI'
  status: string
  inferenceStatus?: 'STARTED' | 'COMPLETED' | 'FAILED' | null
  executionMode?: ExecutionMode | null
  origin?: CaseOrigin | null
  demoCategory?: DemoCategory | null
  demoCaseSlug?: string | null
  demoManifestVersion?: string | null
  sourceDataset?: string | null
  sourceAttribution?: string | null
  createdAt: string
  updatedAt?: string
}

export type StatusPayload = {
  caseId: number
  status: string
  inferenceStatus: 'STARTED' | 'COMPLETED' | 'FAILED' | null
  executionMode: ExecutionMode | null
  modelVersion: string | null
  metrics: MlMetrics | null
  failureDetails: ProcessDetails | null
  resultReady: boolean
  resultSource: CaseResultSource
  stageAuditTrail: Array<{ action: AuditAction; at: string; details?: ProcessDetails | null }>
}

export type ArtifactItem = {
  id: number
  type: ArtifactType
  mimeType: string
  fileName: string
  downloadUrl: string
}

export type FindingItem = {
  id: number
  type: FindingType
  label: string
  confidence: number | null
  sizeMm: number | null
  volumeMm3: number | null
  location: FindingLocation | null
}

export type Viewer3DPayload = {
  liverMeshArtifactId: number | null
  lesionMeshArtifactId: number | null
}
