package com.diploma.mrt.config;

import com.diploma.mrt.entity.ExecutionMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Ml ml,
        String publicBaseUrl,
        List<String> additionalAllowedOrigins
) {
    public AppProperties {
        ml = ml == null ? new Ml(null, ExecutionMode.MOCK) : ml;
        publicBaseUrl = publicBaseUrl == null || publicBaseUrl.isBlank() ? "http://localhost" : publicBaseUrl;
        additionalAllowedOrigins = additionalAllowedOrigins == null ? List.of() : List.copyOf(additionalAllowedOrigins);
    }

    public record Ml(String url, ExecutionMode mode) {
        public Ml {
            mode = mode == null ? ExecutionMode.MOCK : mode;
        }
    }
}
