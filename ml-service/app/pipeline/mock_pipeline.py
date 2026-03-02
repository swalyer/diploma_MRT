import json
from app.pipeline.base import Pipeline
from app.schemas.infer_request import InferRequest
from app.schemas.infer_response import InferResponse, Finding
from app.postprocessing.report_builder import build_report


class MockPipeline(Pipeline):
    def run(self, request: InferRequest) -> InferResponse:
        input_key = request.fileReferences['inputObjectKey']
        suspicious = request.caseId % 2 == 1
        findings = [Finding(type='LESION', label='Mock suspicious lesion', confidence=0.91, sizeMm=12.1, volumeMm3=889.0, locationJson='{"segment":"S6"}')] if suspicious else []
        report_text, report_json = build_report(request.modality, 'mock', len(findings))
        return InferResponse(
            status='COMPLETED',
            modelVersion='mock-v2',
            metricsJson=json.dumps({'mode': 'mock'}),
            reportText=report_text,
            reportJson=report_json,
            findings=findings,
            enhancedObjectKey=f'{input_key}.enhanced.nii.gz',
            liverMaskObjectKey=f'{input_key}.liver_mask.nii.gz',
            lesionMaskObjectKey=f'{input_key}.lesion_mask.nii.gz',
            liverMeshObjectKey=f'{input_key}.liver.glb',
            lesionMeshObjectKey=f'{input_key}.lesion.glb',
        )
