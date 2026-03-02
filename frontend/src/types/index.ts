export type CaseItem = {
  id: number
  patientPseudoId: string
  modality: 'CT' | 'MRI'
  status: string
  createdAt: string
  updatedAt?: string
}

export type StatusPayload = {
  caseId: number
  status: string
  inferenceStatus: string
  executionMode: string
  modelVersion: string
  metricsJson: string | null
  stageAuditTrail: Array<{ action: string; at: string }>
}

export type ArtifactItem = {
  id: number
  type:
    | 'ORIGINAL_STUDY'
    | 'NORMALIZED_VOLUME'
    | 'ENHANCED_VOLUME'
    | 'LIVER_MASK'
    | 'LESION_MASK'
    | 'LIVER_MESH'
    | 'LESION_MESH'
    | string
  mimeType: string
  fileName: string
  downloadUrl: string
}

export type FindingItem = {
  id: number
  type: string
  label: string
  confidence: number | null
  sizeMm: number | null
  volumeMm3: number | null
  locationJson: string | null
}

export type Viewer3DPayload = {
  liverMeshArtifactId: number | null
  lesionMeshArtifactId: number | null
}
