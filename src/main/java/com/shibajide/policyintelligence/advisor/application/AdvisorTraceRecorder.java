package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.trace.application.RetrievalTraceTimings;
import com.shibajide.policyintelligence.trace.infrastructure.RetrievalTraceRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
class AdvisorTraceRecorder {

    private final RetrievalTraceRepository traceRepository;

    AdvisorTraceRecorder(RetrievalTraceRepository traceRepository) {
        this.traceRepository = traceRepository;
    }

    TraceStep save(
            String question,
            Instant requestStarted,
            AdvisorQueryPlan plan,
            RetrievalStep retrieval,
            ContextStep context,
            AnswerStep answer,
            PredictionStep prediction
    ) {
        var traceId = traceRepository.save(
                question,
                plan.refinedQuery(),
                answer.answer(),
                retrieval.chunks(),
                context.context().usedChunks(),
                context.context().decisions(),
                context.context().metrics(),
                prediction.prediction(),
                0,
                false,
                timings(requestStarted, retrieval, context, answer, prediction),
                answer.generatorName(),
                "HYBRID_MULTI_QUERY_RERANKED",
                String.join(" || ", plan.retrievalQueries()),
                plan.rewrite().latencyMs(),
                plan.rewrite().status(),
                answer.verification().verified(),
                answer.verification().reason()
        );
        return new TraceStep(traceId);
    }

    private RetrievalTraceTimings timings(
            Instant requestStarted,
            RetrievalStep retrieval,
            ContextStep context,
            AnswerStep answer,
            PredictionStep prediction
    ) {
        return new RetrievalTraceTimings(
                retrieval.latencyMs(),
                context.latencyMs(),
                answer.latencyMs(),
                prediction.latencyMs(),
                elapsedMs(requestStarted)
        );
    }

    private long elapsedMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }
}

