package com.diploma.mrt.demo;

import com.diploma.mrt.demo.manifest.DemoManifest;
import com.diploma.mrt.demo.manifest.DemoManifestSchemaVersion;
import com.diploma.mrt.entity.CaseOrigin;
import com.diploma.mrt.entity.DemoCategory;
import com.diploma.mrt.entity.Modality;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DemoManifestContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void deserializesTypedDemoManifestV1() throws Exception {
        String payload = """
                {
                  "schemaVersion": "v1",
                  "caseSlug": "ct-single-lesion-001",
                  "origin": "SEEDED_DEMO",
                  "modality": "CT",
                  "category": "SINGLE_LESION",
                  "patientPseudoId": "demo-ct-001",
                  "sourceDataset": "MSD Task03 Liver",
                  "sourceAttribution": "Synthetic attribution",
                  "artifacts": [
                    {
                      "type": "ORIGINAL_STUDY",
                      "objectKey": "demo/cases/ct-single-lesion-001/input.nii.gz",
                      "fileName": "input.nii.gz",
                      "mimeType": "application/gzip",
                      "sha256": "abc123",
                      "sizeBytes": 42
                    }
                  ],
                  "findings": [
                    {
                      "type": "LESION",
                      "label": "Segment 6 lesion",
                      "confidence": null,
                      "sizeMm": 12.4,
                      "volumeMm3": 810.0,
                      "location": {
                        "segment": "S6"
                      }
                    }
                  ],
                  "reportData": {
                    "findings": "One lesion component is present.",
                    "impression": "Single lesion component in liver.",
                    "limitations": "Demo case.",
                    "recommendation": "Review source images."
                  },
                  "reportText": "Deterministic report text"
                }
                """;

        DemoManifest manifest = objectMapper.readValue(payload, DemoManifest.class);

        assertEquals(DemoManifestSchemaVersion.V1, manifest.schemaVersion());
        assertEquals(CaseOrigin.SEEDED_DEMO, manifest.origin());
        assertEquals(Modality.CT, manifest.modality());
        assertEquals(DemoCategory.SINGLE_LESION, manifest.category());
        assertEquals("demo/cases/ct-single-lesion-001/input.nii.gz", manifest.artifacts().get(0).objectKey());
        assertEquals("Single lesion component in liver.", manifest.reportData().impression());
    }
}
