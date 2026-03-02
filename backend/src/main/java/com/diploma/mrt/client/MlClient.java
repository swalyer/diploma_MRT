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

    public MlClient(@Value("${app.ml-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public CaseDtos.MlResult infer(Long caseId, String modality, String inputPath) {
        return restClient.post().uri("/infer/case")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("caseId", caseId, "modality", modality, "fileReferences", Map.of("input", inputPath)))
                .retrieve()
                .body(CaseDtos.MlResult.class);
    }
}
