export type CaseItem = { id:number; patientPseudoId:string; modality:string; status:string; createdAt:string }
export type StatusPayload = { caseId:number; status:string; inferenceStatus:string; stageAuditTrail:Array<{action:string; at:string}> }
export type ArtifactItem = { id:number; type:string; mimeType:string; fileName:string; downloadUrl:string }
