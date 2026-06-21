package com.acme.policyintelligence.ml.api;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/v1/ml")
public class MlHealthController {

    private final RestClient restClient;
    private final String baseUrl;
    private final boolean enabled;

    public MlHealthController(
            RestClient.Builder builder,
            @Value("${app.ml.base-url:http://127.0.0.1:8090}") String baseUrl,
            @Value("${app.ml.enabled:true}") boolean enabled
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory())
                .build();
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    @GetMapping("/health")
    public MlHealthStatus health() {
        if (!enabled) {
            return new MlHealthStatus(false, false, false, baseUrl, "ML integration is disabled.");
        }
        try {
            MlServiceHealth health = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(MlServiceHealth.class);
            if (health == null) {
                return new MlHealthStatus(true, false, false, baseUrl, "ML service returned an empty response.");
            }
            return new MlHealthStatus(true, true, health.modelLoaded(), baseUrl, health.status());
        } catch (RuntimeException exception) {
            return new MlHealthStatus(true, false, false, baseUrl, exception.getMessage());
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    private record MlServiceHealth(String status, boolean modelLoaded) {
    }
}
