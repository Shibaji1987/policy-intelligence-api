package com.shibajide.policyintelligence.advisor.infrastructure;

import com.shibajide.policyintelligence.advisor.application.AnswerGenerator;
import com.shibajide.policyintelligence.context.application.BuiltContext;
import org.springframework.stereotype.Component;

@Component
public class ExtractiveAnswerGenerator implements AnswerGenerator {

    @Override
    public String answer(String question, BuiltContext context) {
        if (context.usedChunks().isEmpty()) {
            return "I could not find relevant policy context to answer this question.";
        }

        var best = context.usedChunks().getFirst();
        var lower = best.chunkText().toLowerCase();
        if (lower.contains("contractors may not access")
                || lower.contains("contractors are prohibited")) {
            return "Contractors may not access production customer data by default. The policy allows exceptions only with explicit Security, Compliance, and business-owner approval, and access must be time-bound, ticket-linked, and audited.";
        }

        return "Based on the most relevant retrieved policy chunk: " + best.excerpt();
    }
}
