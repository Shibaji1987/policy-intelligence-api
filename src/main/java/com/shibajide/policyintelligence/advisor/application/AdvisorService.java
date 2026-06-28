package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.context.application.ContextManager;
import com.shibajide.policyintelligence.advisor.queryexpansion.QueryExpansionRequest;
import com.shibajide.policyintelligence.advisor.queryexpansion.QueryExpansionService;
import com.shibajide.policyintelligence.advisor.queryrewrite.QueryRewriteRequest;
import com.shibajide.policyintelligence.advisor.queryrewrite.QueryRewriteService;
import com.shibajide.policyintelligence.ml.application.RetrievalQualityFeatures;
import com.shibajide.policyintelligence.ml.application.RetrievalQualityPredictor;
import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;
import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;
import com.shibajide.policyintelligence.retrieval.hybrid.HybridRetrievalService;
import com.shibajide.policyintelligence.retrieval.infrastructure.VectorSearchRepository;
import com.shibajide.policyintelligence.retrieval.rerank.Reranker;
import com.shibajide.policyintelligence.shared.text.TokenEstimator;
import com.shibajide.policyintelligence.trace.application.RetrievalTraceTimings;
import com.shibajide.policyintelligence.trace.infrastructure.RetrievalTraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdvisorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvisorService.class);
    private static final int ADVISOR_RETRIEVAL_TOP_K = 50;
    private static final int ADVISOR_RERANKED_LIMIT = 20;
    private static final int PARENT_CHILD_SEED_LIMIT = 8;

    private final QueryRewriteService queryRewriteService;
    private final QueryExpansionService queryExpansionService;
    private final HybridRetrievalService hybridRetrievalService;
    private final ContextManager contextManager;
    private final Reranker reranker;
    private final VectorSearchRepository vectorSearchRepository;
    private final AnswerGenerator answerGenerator;
    private final AnswerVerifier answerVerifier;
    private final RetrievalQualityPredictor qualityPredictor;
    private final RetrievalTraceRepository traceRepository;
    private final TokenEstimator tokenEstimator;

    public AdvisorService(
            QueryRewriteService queryRewriteService,
            QueryExpansionService queryExpansionService,
            HybridRetrievalService hybridRetrievalService,
            ContextManager contextManager,
            Reranker reranker,
            VectorSearchRepository vectorSearchRepository,
            AnswerGenerator answerGenerator,
            AnswerVerifier answerVerifier,
            RetrievalQualityPredictor qualityPredictor,
            RetrievalTraceRepository traceRepository,
            TokenEstimator tokenEstimator
    ) {
        this.queryRewriteService = queryRewriteService;
        this.queryExpansionService = queryExpansionService;
        this.hybridRetrievalService = hybridRetrievalService;
        this.contextManager = contextManager;
        this.reranker = reranker;
        this.vectorSearchRepository = vectorSearchRepository;
        this.answerGenerator = answerGenerator;
        this.answerVerifier = answerVerifier;
        this.qualityPredictor = qualityPredictor;
        this.traceRepository = traceRepository;
        this.tokenEstimator = tokenEstimator;
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
            var rewrite = queryRewriteService.rewrite(new QueryRewriteRequest(question));
            String refinedQuery = rewrite.rewrittenQuery();
            var expansion = queryExpansionService.expand(new QueryExpansionRequest(refinedQuery));
            List<String> retrievalQueries = expansion.generatedQueries().stream()
                    .map(generated -> generated.query())
                    .toList();
            if (retrievalQueries.isEmpty() && !refinedQuery.isBlank()) {
                retrievalQueries = List.of(refinedQuery);
            }
            if (retrievalQueries.isEmpty()) {
                throw new IllegalArgumentException("Question must produce at least one retrieval query");
            }
            sink.emit(AdvisorEvent.of(AdvisorStage.QUERY_REFINED, "Query refined", Map.of(
                    "query", refinedQuery,
                    "rewriteStrategy", rewrite.rewriteStrategy(),
                    "rewriteLatencyMs", rewrite.latencyMs(),
                    "retrievalQueries", retrievalQueries
            )));

            sink.emit(AdvisorEvent.of(AdvisorStage.VECTOR_SEARCH_STARTED, "Vector search started"));
            Instant retrievalStarted = Instant.now();
            var retrieval = hybridRetrievalService.search(question, refinedQuery, retrievalQueries, ADVISOR_RETRIEVAL_TOP_K, filters);
            long retrievalLatencyMs = elapsedMs(retrievalStarted);
            List<RetrievedChunk> retrieved = mergeRetrieved(retrieval.fusedResults());
            var neighbors = vectorSearchRepository.findActiveNeighbors(retrieved.stream().limit(PARENT_CHILD_SEED_LIMIT).toList());
            retrieved = reranker.rerank(question, mergeRetrieved(List.copyOf(concat(retrieved, neighbors)))).stream()
                    .limit(ADVISOR_RERANKED_LIMIT)
                    .map(reranked -> reranked.toRetrievedChunk())
                    .toList();
            LOGGER.info(
                    "Advisor retrieval completed. plannedQueries={}, retrievedChunks={}",
                    retrieval.expandedQueries().size(),
                    retrieved.size()
            );
            sink.emit(AdvisorEvent.of(
                    AdvisorStage.CHUNKS_RETRIEVED,
                    "Chunks retrieved",
                    Map.of(
                            "count", retrieved.size(),
                            "plannedQueries", retrieval.expandedQueries().size(),
                            "latencyMs", retrievalLatencyMs,
                            "retrievalStatus", retrieval.status(),
                            "retrievalStrategy", retrieval.retrievalStrategy(),
                            "queryId", retrieval.queryId()
                    )
            ));

            sink.emit(AdvisorEvent.of(AdvisorStage.CONTEXT_FILTERING_STARTED, "Context filtering started"));
            Instant contextStarted = Instant.now();
            var context = contextManager.build(question, retrieved);
            long contextLatencyMs = elapsedMs(contextStarted);
            int questionTokens = tokenEstimator.estimate(question);
            LOGGER.info(
                    "Advisor context built. usedChunks={}, discardedChunks={}, estimatedTokens={}, documentDiversity={}",
                    context.metrics().usedChunks(),
                    context.metrics().discardedChunks(),
                    context.metrics().estimatedTokens(),
                    context.metrics().documentDiversity()
            );
            var contextEventDetails = new LinkedHashMap<String, Object>();
            contextEventDetails.put("questionTokens", questionTokens);
            contextEventDetails.put("usedChunks", context.metrics().usedChunks());
            contextEventDetails.put("discardedChunks", context.metrics().discardedChunks());
            contextEventDetails.put("estimatedTokens", context.metrics().estimatedTokens());
            contextEventDetails.put("totalEstimatedInputTokens", questionTokens + context.metrics().estimatedTokens());
            contextEventDetails.put("documentDiversity", context.metrics().documentDiversity());
            contextEventDetails.put("duplicateDiscardedChunks", context.metrics().duplicateDiscardedChunks());
            contextEventDetails.put("nearDuplicateDiscardedChunks", context.metrics().nearDuplicateDiscardedChunks());
            contextEventDetails.put("documentQuotaDiscardedChunks", context.metrics().documentQuotaDiscardedChunks());
            contextEventDetails.put("tokenBudgetDiscardedChunks", context.metrics().tokenBudgetDiscardedChunks());
            contextEventDetails.put("maxChunkDiscardedChunks", context.metrics().maxChunkDiscardedChunks());
            contextEventDetails.put("latencyMs", contextLatencyMs);
            sink.emit(AdvisorEvent.of(
                    AdvisorStage.CONTEXT_BUILT,
                    "Context built",
                    contextEventDetails
            ));

            sink.emit(AdvisorEvent.of(AdvisorStage.LLM_STARTED, "Answer generation started"));
            Instant llmStarted = Instant.now();
            String answer = answerGenerator.answer(question, context);
            long llmLatencyMs = elapsedMs(llmStarted);
            var verification = answerVerifier.verify(answer, context);
            sink.emit(AdvisorEvent.of(AdvisorStage.ANSWER_VERIFIED, "Answer verified", Map.of(
                    "verified", verification.verified(),
                    "reason", verification.reason(),
                    "confidence", verification.confidence(),
                    "unsupportedClaims", verification.unsupportedClaims()
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
                    0,
                    false,
                    new RetrievalTraceTimings(
                            retrievalLatencyMs,
                            contextLatencyMs,
                            llmLatencyMs,
                            mlLatencyMs,
                            elapsedMs(requestStarted)
                    ),
                    answerGenerator.name(),
                    "HYBRID_MULTI_QUERY_RERANKED",
                    String.join(" || ", retrievalQueries),
                    rewrite.latencyMs(),
                    rewrite.status(),
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
