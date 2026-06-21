package com.acme.policyintelligence.advisor.application;

import com.acme.policyintelligence.context.application.BuiltContext;
import org.springframework.stereotype.Component;

@Component
public class AnswerVerifier {

    public AnswerVerification verify(String answer, BuiltContext context) {
        if (context.usedChunks().isEmpty()) {
            return new AnswerVerification(false, "NO_CONTEXT_CHUNKS");
        }
        if (answer == null || answer.isBlank()) {
            return new AnswerVerification(false, "EMPTY_ANSWER");
        }
        if (!answer.contains("[Source") && !answer.toLowerCase().contains("source")) {
            return new AnswerVerification(false, "ANSWER_DOES_NOT_CITE_SOURCE");
        }
        return new AnswerVerification(true, "ANSWER_CITES_RETRIEVED_CONTEXT");
    }
}
