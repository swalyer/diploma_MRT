package com.diploma.mrt.service.impl;

import com.diploma.mrt.client.contract.MlInferenceRequest;
import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.entity.Modality;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MlInferenceRequestFactory {
    public MlInferenceRequest build(
            Long caseId,
            Long runId,
            Modality modality,
            String inputObjectKey,
            ExecutionMode executionMode
    ) {
        return new MlInferenceRequest(
                "v1",
                caseId,
                modality,
                executionMode,
                new MlInferenceRequest.FileReferences(inputObjectKey),
                new MlInferenceRequest.RequestMetadata(UUID.randomUUID().toString(), runId)
        );
    }
}
