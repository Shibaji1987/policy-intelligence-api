package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.context.application.ContextMetrics;
import com.shibajide.policyintelligence.ml.application.RetrievalQualityPrediction;
import com.shibajide.policyintelligence.trace.application.RetrievalTraceTimings;
import com.shibajide.policyintelligence.trace.infrastructure.RetrievalTraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
class AdvisorNoRetrievalResponder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvisorNoRetrievalResponder.class);

    private final RetrievalTraceRepository traceRepository;

    AdvisorNoRetrievalResponder(RetrievalTraceRepository traceRepository) {
        this.traceRepository = traceRepository;
    }

    AdvisorAnswer answer(String question, QueryRoute route, Instant requestStarted, AdvisorEventSink sink) {
        var metrics = emptyMetrics();
        var prediction = new RetrievalQualityPrediction(route.reason(), 1.0, "query-router");
        var traceId = saveTrace(question, route, requestStarted, metrics, prediction);

        emitEvents(route, traceId, requestStarted, sink);
        LOGGER.info("Advisor request completed without retrieval. traceId={}, intent={}, reason={}",
                traceId, route.intent(), route.reason());
        return new AdvisorAnswer(traceId, question, question, route.response(), metrics, prediction, List.of());
    }

    private java.util.UUID saveTrace(
            String question,
            QueryRoute route,
            Instant requestStarted,
            ContextMetrics metrics,
            RetrievalQualityPrediction prediction
    ) {
        return traceRepository.save(
                question,
                question,
                route.response(),
                List.of(),
                List.of(),
                List.of(),
                metrics,
                prediction,
                0,
                false,
                new RetrievalTraceTimings(0, 0, 0, 0, elapsedMs(requestStarted)),
                "query_router",
                "NO_RETRIEVAL",
                route.intent().name(),
                0,
                route.reason(),
                true,
                "No retrieval was run because the query router did not classify the message as policy knowledge."
        );
    }

    private void emitEvents(QueryRoute route, java.util.UUID traceId, Instant requestStarted, AdvisorEventSink sink) {
        sink.emit(AdvisorEvent.of(AdvisorStage.ANSWER_VERIFIED, "Conversation context checked", Map.of(
                "retrievalRequired", false,
                "intent", route.intent(),
                "reason", route.reason()
        )));
        sink.emit(AdvisorEvent.of(AdvisorStage.SOURCE_ATTRIBUTION_CREATED, "No RAG sources used", Map.of("sourceCount", 0)));
        sink.emit(AdvisorEvent.of(AdvisorStage.ANSWER_COMPLETED, "Answer completed", Map.of(
                "traceId", traceId,
                "llmLatencyMs", 0,
                "mlLatencyMs", 0,
                "totalLatencyMs", elapsedMs(requestStarted)
        )));
    }

    private ContextMetrics emptyMetrics() {
        return new ContextMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private long elapsedMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }
}

