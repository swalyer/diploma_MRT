package com.diploma.mrt.client;

import com.diploma.mrt.dto.MlDtos;
import com.diploma.mrt.integration.ml.contract.MlContractInferenceRequest;
import com.diploma.mrt.integration.ml.contract.MlContractInferenceResponse;
import com.diploma.mrt.integration.ml.contract.MlContractTypes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class MlClient {
    private final RestClient restClient;
    private final MlContractTypes.ExecutionMode defaultExecutionMode;

    public MlClient(@Value("${app.ml-url}") String baseUrl, @Value("${app.ml-mode:mock}") String executionMode) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.defaultExecutionMode = MlContractTypes.ExecutionMode.fromWireValue(executionMode);
    }

    public MlContractInferenceResponse infer(MlContractInferenceRequest request) {
        MlContractInferenceRequest normalizedRequest = request.executionMode() == null
                ? new MlContractInferenceRequest(
                request.schemaVersion(),
                request.caseId(),
                request.modality(),
                defaultExecutionMode,
                request.fileReferences(),
                request.requestMetadata()
        )
                : request;
        return restClient.post().uri("/v1/infer/case")
                .contentType(MediaType.APPLICATION_JSON)
                .body(normalizedRequest)
                .retrieve()
                .body(MlContractInferenceResponse.class);
    }

    public MlDtos.MlHealthResponse health() {
        return restClient.get()
                .uri("/health")
                .retrieve()
                .body(MlDtos.MlHealthResponse.class);
    }

    public MlDtos.MlCapabilitiesResponse capabilities() {
        return restClient.get()
                .uri("/capabilities")
                .retrieve()
                .body(MlDtos.MlCapabilitiesResponse.class);
    }
}
