package com.diploma.mrt.client;

import com.diploma.mrt.config.AppProperties;
import com.diploma.mrt.dto.MlDtos;
import com.diploma.mrt.entity.ExecutionMode;
import com.diploma.mrt.integration.ml.MlInferenceRequest;
import com.diploma.mrt.integration.ml.MlInferenceResponse;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class MlClient {
    private final RestClient restClient;
    private final ExecutionMode defaultExecutionMode;

    public MlClient(AppProperties appProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(appProperties.ml().requestTimeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(appProperties.ml().url())
                .requestFactory(requestFactory)
                .build();
        this.defaultExecutionMode = appProperties.ml().mode();
    }

    public MlInferenceResponse infer(MlInferenceRequest request) {
        return restClient.post().uri("/v1/infer/case")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request.withDefaultExecutionMode(defaultExecutionMode))
                .retrieve()
                .body(MlInferenceResponse.class);
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
