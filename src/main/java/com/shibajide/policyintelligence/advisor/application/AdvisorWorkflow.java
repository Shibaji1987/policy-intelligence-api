package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
class AdvisorWorkflow {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvisorWorkflow.class);

    private final AdvisorQueryPlanner queryPlanner;
    private final AdvisorRetriever retriever;
    private final AdvisorContextBuilder contextBuilder;
    private final AdvisorAnswerer answerer;
    private final AdvisorQualityScorer qualityScorer;
    private final AdvisorTraceRecorder traceRecorder;

    AdvisorWorkflow(
            AdvisorQueryPlanner queryPlanner,
            AdvisorRetriever retriever,
            AdvisorContextBuilder contextBuilder,
            AdvisorAnswerer answerer,
            AdvisorQualityScorer qualityScorer,
            AdvisorTraceRecorder traceRecorder
    ) {
        this.queryPlanner = queryPlanner;
        this.retriever = retriever;
        this.contextBuilder = contextBuilder;
        this.answerer = answerer;
        this.qualityScorer = qualityScorer;
        this.traceRecorder = traceRecorder;
    }

    AdvisorAnswer answer(String question, RetrievalFilters filters, Instant requestStarted, AdvisorEventSink sink) {
        AdvisorQueryPlan plan = queryPlanner.plan(question, sink);
        RetrievalStep retrieval = retriever.retrieve(question, filters, plan, sink);
        ContextStep context = contextBuilder.build(question, retrieval.chunks(), sink);
        AnswerStep answer = answerer.answer(question, context.context(), sink);
        PredictionStep prediction = qualityScorer.predict(question, retrieval.chunks(), context.context());
        TraceStep trace = traceRecorder.save(question, requestStarted, plan, retrieval, context, answer, prediction);

        LOGGER.info("Advisor request completed. traceId={}", trace.traceId());
        return complete(question, plan, context, answer, prediction, trace, requestStarted, sink);
    }

    private AdvisorAnswer complete(
            String question,
            AdvisorQueryPlan plan,
            ContextStep context,
            AnswerStep answer,
            PredictionStep prediction,
            TraceStep trace,
            Instant requestStarted,
            AdvisorEventSink sink
    ) {
        sink.emit(AdvisorEvent.of(
                AdvisorStage.SOURCE_ATTRIBUTION_CREATED,
                "Source attribution created",
                Map.of("sourceCount", context.context().usedChunks().size())
        ));
        sink.emit(AdvisorEvent.of(AdvisorStage.ANSWER_COMPLETED, "Answer completed", Map.of(
                "traceId", trace.traceId(),
                "llmLatencyMs", answer.latencyMs(),
                "mlLatencyMs", prediction.latencyMs(),
                "totalLatencyMs", elapsedMs(requestStarted)
        )));
        return new AdvisorAnswer(
                trace.traceId(),
                question,
                plan.refinedQuery(),
                answer.answer(),
                context.context().metrics(),
                prediction.prediction(),
                context.context().usedChunks()
        );
    }

    private long elapsedMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }
}

