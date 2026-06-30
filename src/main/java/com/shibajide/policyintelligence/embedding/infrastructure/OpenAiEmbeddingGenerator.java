package com.shibajide.policyintelligence.embedding.infrastructure;

import com.shibajide.policyintelligence.embedding.application.EmbeddingGenerator;
import com.shibajide.policyintelligence.embedding.application.EmbeddingVector;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.embeddings.provider", havingValue = "openai")
public class OpenAiEmbeddingGenerator implements EmbeddingGenerator {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final int dimension;

    public OpenAiEmbeddingGenerator(
            RestClient.Builder builder,
            @Value("${app.embeddings.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.embeddings.openai.api-key:}") String apiKey,
            @Value("${app.embeddings.openai.model:text-embedding-3-small}") String model,
            @Value("${app.embeddings.dimension:1536}") int dimension
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory())
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.dimension = dimension;
    }

    @Override
    @CircuitBreaker(name = "openaiEmbeddings")
    public EmbeddingVector embed(String text) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OPENAI_API_KEY is required when app.embeddings.provider=openai.");
        }

        EmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .headers(headers -> headers.setBearerAuth(apiKey))
                .body(new EmbeddingRequest(model, text == null ? "" : text))
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("OpenAI embeddings response did not contain an embedding.");
        }

        List<Double> embedding = response.data().getFirst().embedding();
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalStateException("OpenAI embeddings response contained an empty embedding.");
        }

        float[] values = new float[embedding.size()];
        for (int index = 0; index < embedding.size(); index++) {
            values[index] = embedding.get(index).floatValue();
        }
        return new EmbeddingVector(model, values.length == 0 ? dimension : values.length, values);
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }

    private record EmbeddingRequest(String model, String input) {
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(List<Double> embedding) {
    }
}
