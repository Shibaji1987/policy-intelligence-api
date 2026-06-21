package com.acme.policyintelligence.advisor.application;

import com.acme.policyintelligence.context.application.ContextManager;
import com.acme.policyintelligence.ml.application.RetrievalQualityFeatures;
import com.acme.policyintelligence.ml.application.RetrievalQualityPredictor;
import com.acme.policyintelligence.retrieval.application.RetrievalSearchService;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.trace.application.RetrievalTraceTimings;
import com.acme.policyintelligence.trace.infrastructure.RetrievalTraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AdvisorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvisorService.class);
    private static final int ADVISOR_RETRIEVAL_TOP_K = 20;

    private final QueryRefiner queryRefiner;
    private final RetrievalSearchService retrievalSearchService;
    private final ContextManager contextManager;
    private final AnswerGenerator answerGenerator;
    private final RetrievalQualityPredictor qualityPredictor;
    private final RetrievalTraceRepository traceRepository;

    public AdvisorService(
            QueryRefiner queryRefiner,
            RetrievalSearchService retrievalSearchService,
            ContextManager contextManager,
            AnswerGenerator answerGenerator,
            RetrievalQualityPredictor qualityPredictor,
            RetrievalTraceRepository traceRepository
    ) {
        this.queryRefiner = queryRefiner;
        this.retrievalSearchService = retrievalSearchService;
        this.contextManager = contextManager;
        this.answerGenerator = answerGenerator;
        this.qualityPredictor = qualityPredictor;
        this.traceRepository = traceRepository;
    }

    public AdvisorAnswer answer(String question) {
        return answer(question, event -> {
        });
    }

    public AdvisorAnswer answer(String question, AdvisorEventSink sink) {
        try {
            Instant requestStarted = Instant.now();
            LOGGER.info("Advisor request started. questionLength={}, retrievalTopK={}", question.length(), ADVISOR_RETRIEVAL_TOP_K);
            sink.emit(AdvisorEvent.of(AdvisorStage.QUESTION_RECEIVED, "Question received"));
            String refinedQuery = queryRefiner.refine(question);
            sink.emit(AdvisorEvent.of(AdvisorStage.QUERY_REFINED, "Query refined", Map.of("query", refinedQuery)));

            sink.emit(AdvisorEvent.of(AdvisorStage.VECTOR_SEARCH_STARTED, "Vector search started"));
            Instant retrievalStarted = Instant.now();
            var retrieval = retrievalSearchService.search(refinedQuery, ADVISOR_RETRIEVAL_TOP_K);
            long retrievalLatencyMs = elapsedMs(retrievalStarted);
            List<RetrievedChunk> retrieved = retrieval.chunks();
            LOGGER.info(
                    "Advisor retrieval completed. retrievedChunks={}, cacheHit={}, embeddingModel={}, embeddingDimension={}",
                    retrieved.size(),
                    retrieval.cacheHit(),
                    retrieval.embeddingModel(),
                    retrieval.embeddingDimension()
            );
            sink.emit(AdvisorEvent.of(
                    AdvisorStage.CHUNKS_RETRIEVED,
                    "Chunks retrieved",
                    Map.of(
                            "count", retrieved.size(),
                            "cacheHit", retrieval.cacheHit(),
                            "corpusVersion", retrieval.corpusVersion(),
                            "latencyMs", retrievalLatencyMs
                    )
            ));

            sink.emit(AdvisorEvent.of(AdvisorStage.CONTEXT_FILTERING_STARTED, "Context filtering started"));
            Instant contextStarted = Instant.now();
            var context = contextManager.build(retrieved);
            long contextLatencyMs = elapsedMs(contextStarted);
            LOGGER.info(
                    "Advisor context built. usedChunks={}, discardedChunks={}, estimatedTokens={}, documentDiversity={}",
                    context.metrics().usedChunks(),
                    context.metrics().discardedChunks(),
                    context.metrics().estimatedTokens(),
                    context.metrics().documentDiversity()
            );
            sink.emit(AdvisorEvent.of(
                    AdvisorStage.CONTEXT_BUILT,
                    "Context built",
                    Map.of(
                            "usedChunks", context.metrics().usedChunks(),
                            "discardedChunks", context.metrics().discardedChunks(),
                            "estimatedTokens", context.metrics().estimatedTokens(),
                            "documentDiversity", context.metrics().documentDiversity(),
                            "duplicateDiscardedChunks", context.metrics().duplicateDiscardedChunks(),
                            "nearDuplicateDiscardedChunks", context.metrics().nearDuplicateDiscardedChunks(),
                            "documentQuotaDiscardedChunks", context.metrics().documentQuotaDiscardedChunks(),
                            "tokenBudgetDiscardedChunks", context.metrics().tokenBudgetDiscardedChunks(),
                            "maxChunkDiscardedChunks", context.metrics().maxChunkDiscardedChunks(),
                            "latencyMs", contextLatencyMs
                    )
            ));

            sink.emit(AdvisorEvent.of(AdvisorStage.LLM_STARTED, "Answer generation started"));
            Instant llmStarted = Instant.now();
            String answer = answerGenerator.answer(question, context);
            long llmLatencyMs = elapsedMs(llmStarted);

            var features = features(question, retrieved, context.metrics().usedChunks(), context.metrics().documentDiversity());
            Instant mlStarted = Instant.now();
            var prediction = qualityPredictor.predict(features);
            long mlLatencyMs = elapsedMs(mlStarted);
            LOGGER.info(
                    "Advisor retrieval quality predicted. label={}, probability={}, modelVersion={}",
                    prediction.label(),
                    prediction.probability(),
                    prediction.modelVersion()
            );
            var traceId = traceRepository.save(
                    question,
                    refinedQuery,
                    answer,
                    retrieved,
                    context.usedChunks(),
                    context.decisions(),
                    context.metrics(),
                    prediction,
                    retrieval.corpusVersion(),
                    retrieval.cacheHit(),
                    new RetrievalTraceTimings(
                            retrievalLatencyMs,
                            contextLatencyMs,
                            llmLatencyMs,
                            mlLatencyMs,
                            elapsedMs(requestStarted)
                    ),
                    answerGenerator.name()
            );

            sink.emit(AdvisorEvent.of(
                    AdvisorStage.SOURCE_ATTRIBUTION_CREATED,
                    "Source attribution created",
                    Map.of("sourceCount", context.usedChunks().size())
            ));
            var advisorAnswer = new AdvisorAnswer(
                    traceId,
                    question,
                    refinedQuery,
                    answer,
                    context.metrics(),
                    prediction,
                    context.usedChunks()
            );
            sink.emit(AdvisorEvent.of(
                    AdvisorStage.ANSWER_COMPLETED,
                    "Answer completed",
                    Map.of(
                            "traceId", traceId,
                            "llmLatencyMs", llmLatencyMs,
                            "mlLatencyMs", mlLatencyMs,
                            "totalLatencyMs", elapsedMs(requestStarted)
                    )
            ));
            LOGGER.info("Advisor request completed. traceId={}", traceId);
            return advisorAnswer;
        } catch (RuntimeException exception) {
            LOGGER.warn("Advisor request failed.", exception);
            sink.emit(AdvisorEvent.of(AdvisorStage.ANSWER_FAILED, exception.getMessage()));
            throw exception;
        }
    }

    private long elapsedMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }

    private RetrievalQualityFeatures features(
            String question,
            List<RetrievedChunk> retrieved,
            int usedChunkCount,
            int documentDiversity
    ) {
        double topSimilarity = retrieved.isEmpty() ? 0 : retrieved.getFirst().similarityScore();
        double avgTop5 = retrieved.stream().limit(5).mapToDouble(RetrievedChunk::similarityScore).average().orElse(0);
        return new RetrievalQualityFeatures(
                topSimilarity,
                avgTop5,
                documentDiversity,
                usedChunkCount,
                question.length()
        );
    }
}
