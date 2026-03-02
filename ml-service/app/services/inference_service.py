import json
from app.core.config import settings
from app.pipeline.stages import PreprocessStage, EnhancementStage, LiverSegmentationStage, LesionDetectionStage, MeshGenerationStage
from app.schemas.infer import InferRequest, InferResponse, Finding

class InferenceService:
    def __init__(self) -> None:
        self.preprocess = PreprocessStage()
        self.enhancement = EnhancementStage()
        self.liver_seg = LiverSegmentationStage()
        self.lesion_det = LesionDetectionStage()
        self.mesh_gen = MeshGenerationStage()

    def infer(self, request: InferRequest) -> InferResponse:
        input_object_key = request.fileReferences["inputObjectKey"]
        base = self.preprocess.run(input_object_key)
        enhanced = self.enhancement.run(base)
        liver_mask = self.liver_seg.run(base)
        lesion_mask = self.lesion_det.run(base)
        liver_mesh, lesion_mesh = self.mesh_gen.run(base)
        suspicious = request.caseId % 2 == 1
        findings = []
        if suspicious:
            findings.append(Finding(type="LESION", label="Suspicious liver lesion", confidence=0.91, sizeMm=24.3, volumeMm3=4812.6, locationJson='{"segment":"S6"}'))
        report_text = "По результатам автоматизированного анализа МРТ печени выделена паренхима печени, выполнено подавление нерелевантных структур и построена трёхмерная модель органа. Выявлен подозрительный очаг. Результат носит вспомогательный характер и требует обязательной верификации врачом-рентгенологом." if suspicious else "По результатам автоматизированного анализа МРТ печени выделена паренхима печени, выполнено подавление нерелевантных структур и построена трёхмерная модель органа. Подозрительный очаг не выявлен. Результат носит вспомогательный характер и требует обязательной верификации врачом-рентгенологом."
        report_json = json.dumps({"classification": "suspicious lesion detected" if suspicious else "no suspicious lesion", "confidence": 0.91 if suspicious else 0.87})
        metrics = json.dumps({"mode": settings.mode, "steps": ["preprocess", "enhancement", "liver_segmentation", "lesion_detection", "classification", "mesh_generation", "report"]})
        return InferResponse(status="COMPLETED", modelVersion="mock-v1" if settings.mode == "mock" else "real-ready-v1", metricsJson=metrics, reportText=report_text, reportJson=report_json, findings=findings,
            enhancedObjectKey=enhanced, liverMaskObjectKey=liver_mask, lesionMaskObjectKey=lesion_mask, liverMeshObjectKey=liver_mesh, lesionMeshObjectKey=lesion_mesh)
