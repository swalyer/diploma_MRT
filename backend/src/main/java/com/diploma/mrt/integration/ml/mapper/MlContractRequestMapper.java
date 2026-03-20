package com.diploma.mrt.integration.ml.mapper;

import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.entity.Modality;
import com.diploma.mrt.integration.ml.contract.MlContractInferenceRequest;
import com.diploma.mrt.integration.ml.contract.MlContractTypes;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MlContractRequestMapper {
    public MlContractInferenceRequest toContract(
            Long caseId,
            Long runId,
            Modality modality,
            String inputObjectKey,
            ExecutionMode executionMode
    ) {
        return new MlContractInferenceRequest(
                "v1",
                caseId,
                mapModality(modality),
                mapExecutionMode(executionMode),
                new MlContractInferenceRequest.FileReferences(inputObjectKey),
                new MlContractInferenceRequest.RequestMetadata(UUID.randomUUID().toString(), runId)
        );
    }

    private MlContractTypes.Modality mapModality(Modality modality) {
        return switch (modality) {
            case CT -> MlContractTypes.Modality.CT;
            case MRI -> MlContractTypes.Modality.MRI;
        };
    }

    private MlContractTypes.ExecutionMode mapExecutionMode(ExecutionMode executionMode) {
        if (executionMode == null) {
            return null;
        }
        return switch (executionMode) {
            case MOCK -> MlContractTypes.ExecutionMode.MOCK;
            case REAL -> MlContractTypes.ExecutionMode.REAL;
        };
    }
}
