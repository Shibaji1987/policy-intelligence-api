package com.acme.policyintelligence.advisor.application;

import com.acme.policyintelligence.context.application.ContextManager;
import com.acme.policyintelligence.ml.application.RetrievalQualityFeatures;
import com.acme.policyintelligence.ml.application.RetrievalQualityPredictor;
import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.retrieval.application.RetrievalReranker;
import com.acme.policyintelligence.retrieval.application.RetrievalSearchService;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.retrieval.infrastructure.VectorSearchRepository;
import com.acme.policyintelligence.trace.application.RetrievalTraceTimings;
import com.acme.policyintelligence.trace.infrastructure.RetrievalTraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
public class AdvisorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvisorService.class);
    private static final int ADVISOR_RETRIEVAL_TOP_K = 50;
    private static final int ADVISOR_RERANKED_LIMIT = 20;
    private static final int PARENT_CHILD_SEED_LIMIT = 8;

    private final QueryRefiner queryRefiner;
    private final RetrievalSearchService retrievalSearchService;
    private final ContextManager contextManager;
    private final RetrievalReranker retrievalReranker;
    private final VectorSearchRepository vectorSearchRepository;
    private final AnswerGenerator answerGenerator;
    private final AnswerVerifier answerVerifier;
    private final RetrievalQualityPredictor qualityPredictor;
    private final RetrievalTraceRepository traceRepository;

    public AdvisorService(
            QueryRefiner queryRefiner,
            RetrievalSearchService retrievalSearchService,
            ContextManager contextManager,
            RetrievalReranker retrievalReranker,
            VectorSearchRepository vectorSearchRepository,
            AnswerGenerator answerGenerator,
            AnswerVerifier answerVerifier,
            RetrievalQualityPredictor qualityPredictor,
            RetrievalTraceRepository traceRepository
    ) {
        this.queryRefiner = queryRefiner;
        this.retrievalSearchService = retrievalSearchService;
        this.contextManager = contextManager;
        this.retrievalReranker = retrievalReranker;
        this.vectorSearchRepository = vectorSearchRepository;
        this.answerGenerator = answerGenerator;
        this.answerVerifier = answerVerifier;
        this.qualityPredictor = qualityPredictor;
        this.traceRepository = traceRepository;
    }

    public AdvisorAnswer answer(String question) {
        return answer(question, RetrievalFilters.defaults(), event -> {
        });
    }

    public AdvisorAnswer answer(String question, RetrievalFilters filters) {
        return answer(question, filters, event -> {
        });
    }

    public AdvisorAnswer answer(String question, AdvisorEventSink sink) {
        return answer(question, RetrievalFilters.defaults(), sink);
    }

    public AdvisorAnswer answer(String question, RetrievalFilters filters, AdvisorEventSink sink) {
        try {
            Instant requestStarted = Instant.now();
            LOGGER.info("Advisor request started. questionLength={}, retrievalTopK={}", question.length(), ADVISOR_RETRIEVAL_TOP_K);
            sink.emit(AdvisorEvent.of(AdvisorStage.QUESTION_RECEIVED, "Question received"));
            QueryPlan queryPlan = queryRefiner.plan(question);
            String refinedQuery = queryPlan.refinedQuery();
            sink.emit(AdvisorEvent.of(AdvisorStage.QUERY_REFINED, "Query refined", Map.of(
                    "query", refinedQuery,
                    "retrievalQueries", queryPlan.retrievalQueries()
            )));

            sink.emit(AdvisorEvent.of(AdvisorStage.VECTOR_SEARCH_STARTED, "Vector search started"));
            Instant retrievalStarted = Instant.now();
            var retrievals = queryPlan.retrievalQueries().stream()
                    .map(query -> retrievalSearchService.search(query, ADVISOR_RETRIEVAL_TOP_K, filters))
                    .toList();
            long retrievalLatencyMs = elapsedMs(retrievalStarted);
            List<RetrievedChunk> retrieved = mergeRetrieved(retrievals.stream()
                    .flatMap(response -> response.chunks().stream())
                    .toList());
            var neighbors = vectorSearchRepository.findActiveNeighbors(retrieved.stream().limit(PARENT_CHILD_SEED_LIMIT).toList());
            retrieved = retrievalReranker.rerank(question, mergeRetrieved(List.copyOf(concat(retrieved, neighbors))), ADVISOR_RERANKED_LIMIT);
            var firstRetrieval = retrievals.isEmpty() ? null : retrievals.getFirst();
            LOGGER.info(
                    "Advisor retrieval completed. plannedQueries={}, retrievedChunks={}, cacheHit={}, embeddingModel={}, embeddingDimension={}",
                    queryPlan.retrievalQueries().size(),
                    retrieved.size(),
                    firstRetrieval != null && firstRetrieval.cacheHit(),
                    firstRetrieval == null ? "unknown" : firstRetrieval.embeddingModel(),
                    firstRetrieval == null ? 0 : firstRetrieval.embeddingDimension()
            );
            sink.emit(AdvisorEvent.of(
                    AdvisorStage.CHUNKS_RETRIEVED,
                    "Chunks retrieved",
                    Map.of(
                            "count", retrieved.size(),
                            "plannedQueries", queryPlan.retrievalQueries().size(),
                            "cacheHit", firstRetrieval != null && firstRetrieval.cacheHit(),
                            "corpusVersion", firstRetrieval == null ? 0 : firstRetrieval.corpusVersion(),
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
            var verification = answerVerifier.verify(answer, context);
            sink.emit(AdvisorEvent.of(AdvisorStage.ANSWER_VERIFIED, "Answer verified", Map.of(
                    "verified", verification.verified(),
                    "reason", verification.reason()
            )));

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
                    firstRetrieval == null ? 0 : firstRetrieval.corpusVersion(),
                    firstRetrieval != null && firstRetrieval.cacheHit(),
                    new RetrievalTraceTimings(
                            retrievalLatencyMs,
                            contextLatencyMs,
                            llmLatencyMs,
                            mlLatencyMs,
                            elapsedMs(requestStarted)
                    ),
                    answerGenerator.name(),
                    "HYBRID_MULTI_QUERY_RERANKED",
                    String.join(" || ", queryPlan.retrievalQueries()),
                    verification.verified(),
                    verification.reason()
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

    private List<RetrievedChunk> mergeRetrieved(List<RetrievedChunk> chunks) {
        var byId = new LinkedHashMap<java.util.UUID, RetrievedChunk>();
        for (RetrievedChunk chunk : chunks) {
            byId.merge(chunk.chunkId(), chunk, (left, right) -> left.combinedScore() >= right.combinedScore() ? left : right);
        }
        return List.copyOf(byId.values());
    }

    private List<RetrievedChunk> concat(List<RetrievedChunk> first, List<RetrievedChunk> second) {
        var combined = new java.util.ArrayList<RetrievedChunk>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return combined;
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
