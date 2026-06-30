package com.shibajide.policyintelligence.advisor.infrastructure;

import com.shibajide.policyintelligence.advisor.application.AnswerGenerator;
import com.shibajide.policyintelligence.context.application.BuiltContext;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Primary
@Component
public class OpenAiResponsesAnswerGenerator implements AnswerGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiResponsesAnswerGenerator.class);
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final String INSTRUCTIONS = """
            You are Policy Intelligence, an enterprise policy advisor.
            Answer only from the supplied retrieved policy context.
            If the context is insufficient, say that the policy context is insufficient.
            Be concise, operationally precise, and include source references such as [Source 1].
            Do not invent policy requirements, approval paths, or evidence types.
            """;

    private final RestClient restClient;
    private final ExtractiveAnswerGenerator fallback;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    public OpenAiResponsesAnswerGenerator(
            RestClient.Builder builder,
            ExtractiveAnswerGenerator fallback,
            @Value("${app.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.llm.api-key:}") String apiKey,
            @Value("${app.llm.model:gpt-4o}") String model,
            @Value("${app.llm.enabled:true}") boolean enabled
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory())
                .build();
        this.fallback = fallback;
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled;
    }

    @Override
    public String answer(String question, BuiltContext context) {
        if (!enabled || !StringUtils.hasText(apiKey)) {
            LOGGER.info("LLM answer generation skipped. reason={}, fallback=extractive",
                    !enabled ? "disabled" : "missing_api_key");
            return fallback.answer(question, context);
        }
        if (context.usedChunks().isEmpty()) {
            LOGGER.info("LLM answer generation skipped. reason=no_context_chunks, fallback=extractive");
            return fallback.answer(question, context);
        }

        try {
            LOGGER.info(
                    "LLM answer generation started. provider=openai_responses, model={}, usedChunks={}, estimatedTokens={}",
                    model,
                    context.metrics().usedChunks(),
                    context.metrics().estimatedTokens()
            );
            Map<String, Object> response = restClient.post()
                    .uri("/responses")
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(request(question, context))
                    .retrieve()
                    .body(RESPONSE_TYPE);
            String answer = extractAnswer(response, question, context);
            LOGGER.info("LLM answer generation completed. provider=openai_responses, model={}", model);
            return answer;
        } catch (RuntimeException exception) {
            LOGGER.warn("LLM answer generation failed. Falling back to extractive answer.", exception);
            return fallback.answer(question, context);
        }
    }

    @Override
    public String name() {
        return enabled && StringUtils.hasText(apiKey) ? "openai_responses:" + model : "extractive_fallback";
    }

    private Map<String, Object> request(String question, BuiltContext context) {
        return Map.of(
                "model", model,
                "store", false,
                "instructions", INSTRUCTIONS,
                "input", prompt(question, context)
        );
    }

    private String prompt(String question, BuiltContext context) {
        return """
                User question:
                %s

                Retrieved policy context:
                %s

                Final answer requirements:
                - Answer the question directly.
                - Cite the relevant source numbers inline.
                - Mention approval, evidence, escalation, and exceptions only when present in the context.
                - If multiple chunks disagree, explain the uncertainty.
                """.formatted(question, context.text());
    }

    private String extractAnswer(Map<String, Object> response, String question, BuiltContext context) {
        if (response == null) {
            return fallback.answer(question, context);
        }

        Object outputText = response.get("output_text");
        if (outputText instanceof String text && StringUtils.hasText(text)) {
            return text.strip();
        }

        Object output = response.get("output");
        if (output instanceof List<?> items) {
            StringBuilder builder = new StringBuilder();
            for (Object item : items) {
                appendContentText(builder, item);
            }
            String text = builder.toString().strip();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }

        LOGGER.warn("LLM response did not contain output text. Falling back to extractive answer.");
        return fallback.answer(question, context);
    }

    private void appendContentText(StringBuilder builder, Object item) {
        if (!(item instanceof Map<?, ?> outputItem)) {
            return;
        }
        Object content = outputItem.get("content");
        if (!(content instanceof List<?> parts)) {
            return;
        }
        for (Object part : parts) {
            if (part instanceof Map<?, ?> contentPart) {
                Object text = contentPart.get("text");
                if (text instanceof String value && StringUtils.hasText(value)) {
                    builder.append(value).append(System.lineSeparator());
                }
            }
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(45));
        return factory;
    }
}
