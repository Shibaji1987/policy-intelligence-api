package com.shibajide.policyintelligence.ml.infrastructure;

import com.shibajide.policyintelligence.ml.application.RetrievalQualityFeatures;
import com.shibajide.policyintelligence.ml.application.RetrievalQualityPrediction;
import com.shibajide.policyintelligence.ml.application.RetrievalQualityPredictor;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MlServiceRetrievalQualityPredictor implements RetrievalQualityPredictor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MlServiceRetrievalQualityPredictor.class);

    private final RestClient restClient;
    private final boolean enabled;

    public MlServiceRetrievalQualityPredictor(
            RestClient.Builder builder,
            @Value("${app.ml.base-url:http://127.0.0.1:8090}") String baseUrl,
            @Value("${app.ml.enabled:true}") boolean enabled
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory())
                .build();
        this.enabled = enabled;
    }

    @Override
    @CircuitBreaker(name = "mlRetrievalQuality", fallbackMethod = "fallbackPrediction")
    public RetrievalQualityPrediction predict(RetrievalQualityFeatures features) {
        if (!enabled) {
            return RetrievalQualityPrediction.unavailable();
        }
        try {
            RetrievalQualityPrediction prediction = restClient.post()
                    .uri("/predict")
                    .body(features)
                    .retrieve()
                    .body(RetrievalQualityPrediction.class);
            if (prediction == null) {
                LOGGER.warn("ML service returned an empty prediction response.");
                return RetrievalQualityPrediction.unavailable();
            }
            return prediction;
        } catch (RuntimeException exception) {
            LOGGER.warn("ML retrieval quality prediction failed. Falling back to UNKNOWN.", exception);
            return RetrievalQualityPrediction.unavailable();
        }
    }

    RetrievalQualityPrediction fallbackPrediction(RetrievalQualityFeatures features, Throwable throwable) {
        LOGGER.warn("ML circuit breaker fallback used. reason={}", throwable.getClass().getSimpleName());
        return RetrievalQualityPrediction.unavailable();
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}
