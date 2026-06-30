package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.context.application.BuiltContext;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
class AdvisorAnswerer {

    private final AnswerGenerator answerGenerator;
    private final AnswerVerifier answerVerifier;

    AdvisorAnswerer(AnswerGenerator answerGenerator, AnswerVerifier answerVerifier) {
        this.answerGenerator = answerGenerator;
        this.answerVerifier = answerVerifier;
    }

    AnswerStep answer(String question, BuiltContext context, AdvisorEventSink sink) {
        sink.emit(AdvisorEvent.of(AdvisorStage.LLM_STARTED, "Answer generation started"));
        Instant started = Instant.now();
        String answer = answerGenerator.answer(question, context);
        long latencyMs = elapsedMs(started);
        AnswerVerification verification = answerVerifier.verify(answer, context);

        sink.emit(AdvisorEvent.of(AdvisorStage.ANSWER_VERIFIED, "Answer verified", Map.of(
                "verified", verification.verified(),
                "reason", verification.reason(),
                "confidence", verification.confidence(),
                "unsupportedClaims", verification.unsupportedClaims()
        )));
        return new AnswerStep(answer, verification, answerGenerator.name(), latencyMs);
    }

    private long elapsedMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }
}

