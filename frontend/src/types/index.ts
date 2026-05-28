import { ARTIFACT_TYPES, AUDIT_ACTIONS, FINDING_TYPES } from './generated-backend-contract'
import type {
  ArtifactType,
  AuditAction,
  CaseOrigin,
  CaseResultSource,
  CaseStatus,
  DemoCategory,
  ExecutionMode,
  FindingType,
  InferenceStatus,
  Modality,
} from './generated-backend-contract'

export {
  ARTIFACT_TYPES,
  AUDIT_ACTIONS,
  FINDING_TYPES,
} from './generated-backend-contract'

export type {
  ArtifactType,
  AuditAction,
  CaseOrigin,
  CaseResultSource,
  CaseStatus,
  DemoCategory,
  ExecutionMode,
  FindingType,
  InferenceStatus,
  Modality,
} from './generated-backend-contract'

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
  modality: Modality
  executionMode: ExecutionMode | null
  lesionCount: number
  evidenceBound: boolean
  sections: ReportSections
  capabilities: ReportCapabilities
}

export type CaseItem = {
  id: number
  patientPseudoId: string
  modality: Modality
  status: CaseStatus
  inferenceStatus?: InferenceStatus | null
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
  status: CaseStatus
  inferenceStatus: InferenceStatus | null
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
