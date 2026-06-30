package com.shibajide.policyintelligence.advisor.queryrewrite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class LlmQueryRewriteStrategy implements QueryRewriteStrategy {

    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final QueryRewritePromptBuilder promptBuilder;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    public LlmQueryRewriteStrategy(
            RestClient.Builder builder,
            QueryRewritePromptBuilder promptBuilder,
            @Value("${app.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.llm.api-key:}") String apiKey,
            @Value("${app.llm.model:gpt-4o}") String model,
            @Value("${app.query-rewrite.llm-enabled:false}") boolean enabled
    ) {
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory()).build();
        this.promptBuilder = promptBuilder;
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled;
    }

    @Override
    public QueryRewriteResult rewrite(QueryRewriteRequest request) {
        Instant started = Instant.now();
        String original = request.question() == null ? "" : request.question().strip();
        if (!enabled || !StringUtils.hasText(apiKey)) {
            return failed(original, started, "LLM_REWRITE_DISABLED_OR_MISSING_KEY");
        }
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/responses")
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(Map.of(
                            "model", model,
                            "store", false,
                            "instructions", "Rewrite the question as a concise enterprise policy retrieval query. Return only the query text.",
                            "input", promptBuilder.build(original)
                    ))
                    .retrieve()
                    .body(RESPONSE_TYPE);
            String rewritten = extractText(response);
            if (!StringUtils.hasText(rewritten)) {
                return failed(original, started, "EMPTY_LLM_REWRITE");
            }
            return new QueryRewriteResult(
                    original,
                    rewritten.strip(),
                    "LLM",
                    0.90,
                    true,
                    null,
                    Duration.between(started, Instant.now()).toMillis(),
                    "COMPLETED"
            );
        } catch (RuntimeException exception) {
            return failed(original, started, "LLM_REWRITE_FAILED");
        }
    }

    private QueryRewriteResult failed(String original, Instant started, String reason) {
        return new QueryRewriteResult(
                original,
                original,
                "LLM",
                0,
                true,
                reason,
                Duration.between(started, Instant.now()).toMillis(),
                "FAILED"
        );
    }

    private String extractText(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object outputText = response.get("output_text");
        if (outputText instanceof String text) {
            return text;
        }
        Object output = response.get("output");
        if (!(output instanceof List<?> items)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Object item : items) {
            if (item instanceof Map<?, ?> outputItem && outputItem.get("content") instanceof List<?> parts) {
                for (Object part : parts) {
                    if (part instanceof Map<?, ?> contentPart && contentPart.get("text") instanceof String text) {
                        builder.append(text).append(' ');
                    }
                }
            }
        }
        return builder.toString().strip();
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
    }
}
