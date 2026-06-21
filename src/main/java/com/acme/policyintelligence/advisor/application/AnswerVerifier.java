package com.acme.policyintelligence.advisor.application;

import com.acme.policyintelligence.context.application.BuiltContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AnswerVerifier {

    public AnswerVerification verify(String answer, BuiltContext context) {
        if (context.usedChunks().isEmpty()) {
            return new AnswerVerification(false, "NO_CONTEXT_CHUNKS", List.of(), "LOW");
        }
        if (answer == null || answer.isBlank()) {
            return new AnswerVerification(false, "EMPTY_ANSWER", List.of(), "LOW");
        }
        boolean citesSource = answer.contains("[Source") || answer.toLowerCase(Locale.ROOT).contains("source");
        List<String> unsupportedClaims = unsupportedClaims(answer, context);
        if (!citesSource) {
            return new AnswerVerification(false, "ANSWER_DOES_NOT_CITE_SOURCE", unsupportedClaims, "MEDIUM");
        }
        if (!unsupportedClaims.isEmpty()) {
            return new AnswerVerification(false, "ANSWER_HAS_WEAKLY_SUPPORTED_CLAIMS", unsupportedClaims, "MEDIUM");
        }
        return new AnswerVerification(true, "ANSWER_CITES_RETRIEVED_CONTEXT", List.of(), "HIGH");
    }

    private List<String> unsupportedClaims(String answer, BuiltContext context) {
        Set<String> contextTokens = tokenSet(context.text());
        var unsupported = new ArrayList<String>();
        for (String sentence : answer.split("(?<=[.!?])\\s+")) {
            String trimmed = sentence.strip();
            if (trimmed.length() < 35 || trimmed.startsWith("[Source")) {
                continue;
            }
            Set<String> sentenceTokens = tokenSet(trimmed);
            if (sentenceTokens.size() < 4) {
                continue;
            }
            int matches = 0;
            for (String token : sentenceTokens) {
                if (contextTokens.contains(token)) {
                    matches++;
                }
            }
            double supportRatio = (double) matches / sentenceTokens.size();
            if (supportRatio < 0.28) {
                unsupported.add(trimmed);
            }
        }
        return List.copyOf(unsupported);
    }

    private Set<String> tokenSet(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (normalized.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(normalized.split("\\s+"))
                .filter(token -> token.length() > 2)
                .collect(java.util.stream.Collectors.toSet());
    }
}
