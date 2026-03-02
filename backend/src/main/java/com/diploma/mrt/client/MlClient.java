package com.diploma.mrt.client;

import com.diploma.mrt.dto.CaseDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class MlClient {
    private final RestClient restClient;
    private final String executionMode;

    public MlClient(@Value("${app.ml-url}") String baseUrl, @Value("${app.ml-mode:mock}") String executionMode) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.executionMode = executionMode;
    }

    public CaseDtos.MlResult infer(Long caseId, String modality, String inputObjectKey) {
        return restClient.post().uri("/infer/case")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "caseId", caseId,
                        "modality", modality,
                        "executionMode", executionMode,
                        "fileReferences", Map.of("inputObjectKey", inputObjectKey)
                ))
                .retrieve()
                .body(CaseDtos.MlResult.class);
    }
}
