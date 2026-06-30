package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.context.application.ContextManager;
import com.shibajide.policyintelligence.context.application.ContextMetrics;
import com.shibajide.policyintelligence.context.application.BuiltContext;
import com.shibajide.policyintelligence.advisor.queryexpansion.QueryExpansionRequest;
import com.shibajide.policyintelligence.advisor.queryexpansion.QueryExpansionResult;
import com.shibajide.policyintelligence.advisor.queryexpansion.QueryExpansionService;
import com.shibajide.policyintelligence.advisor.queryrewrite.QueryRewriteRequest;
import com.shibajide.policyintelligence.advisor.queryrewrite.QueryRewriteResult;
import com.shibajide.policyintelligence.advisor.queryrewrite.QueryRewriteService;
import com.shibajide.policyintelligence.ml.application.RetrievalQualityFeatures;
import com.shibajide.policyintelligence.ml.application.RetrievalQualityPrediction;
import com.shibajide.policyintelligence.ml.application.RetrievalQualityPredictor;
import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;
import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;
import com.shibajide.policyintelligence.retrieval.hybrid.HybridSearchResult;
import com.shibajide.policyintelligence.retrieval.hybrid.HybridRetrievalService;
import com.shibajide.policyintelligence.retrieval.infrastructure.VectorSearchRepository;
import com.shibajide.policyintelligence.retrieval.rerank.Reranker;
import com.shibajide.policyintelligence.shared.text.TokenEstimator;
import com.shibajide.policyintelligence.trace.application.RetrievalTraceTimings;
import com.shibajide.policyintelligence.trace.infrastructure.RetrievalTraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdvisorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvisorService.class);

    private final int retrievalTopK;
    private final int rerankedLimit;
    private final int parentChildSeedLimit;
    private final QueryRewriteService queryRewriteService;
    private final QueryExpansionService queryExpansionService;
    private final QueryRouter queryRouter;
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
            QueryRouter queryRouter,
            HybridRetrievalService hybridRetrievalService,
            ContextManager contextManager,
            Reranker reranker,
            VectorSearchRepository vectorSearchRepository,
            AnswerGenerator answerGenerator,
            AnswerVerifier answerVerifier,
            RetrievalQualityPredictor qualityPredictor,
            RetrievalTraceRepository traceRepository,
            TokenEstimator tokenEstimator,
            @Value("${app.advisor.retrieval-top-k:30}") int retrievalTopK,
            @Value("${app.advisor.reranked-limit:16}") int rerankedLimit,
            @Value("${app.advisor.parent-child-seed-limit:6}") int parentChildSeedLimit
    ) {
        this.retrievalTopK = Math.clamp(retrievalTopK, 5, 100);
        this.rerankedLimit = Math.clamp(rerankedLimit, 4, 50);
        this.parentChildSeedLimit = Math.clamp(parentChildSeedLimit, 0, 20);
        this.queryRewriteService = queryRewriteService;
        this.queryExpansionService = queryExpansionService;
        this.queryRouter = queryRouter;
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
            LOGGER.info("Advisor request started. questionLength={}, retrievalTopK={}", question.length(), retrievalTopK);
            sink.emit(AdvisorEvent.of(AdvisorStage.QUESTION_RECEIVED, "Question received"));

            QueryRoute route = queryRouter.route(question);
            if (!route.retrievalRequired()) {
                return answerWithoutRetrieval(question, route, requestStarted, sink);
            }

            QueryPlan plan = planQueries(question, sink);
            RetrievalStep retrieval = retrieve(question, filters, plan, sink);
            ContextStep context = buildContext(question, retrieval.chunks(), sink);
            AnswerStep answer = generateAnswer(question, context.context(), sink);
            PredictionStep prediction = predictQuality(question, retrieval.chunks(), context.context());
            TraceStep trace = saveTrace(question, requestStarted, plan, retrieval, context, answer, prediction);
            AdvisorAnswer advisorAnswer = complete(question, plan, context, answer, prediction, trace, requestStarted, sink);

            LOGGER.info("Advisor request completed. traceId={}", trace.traceId());
            return advisorAnswer;
        } catch (RuntimeException exception) {
            LOGGER.warn("Advisor request failed.", exception);
            sink.emit(AdvisorEvent.of(AdvisorStage.ANSWER_FAILED, exception.getMessage()));
            throw exception;
        }
    }

    private QueryPlan planQueries(String question, AdvisorEventSink sink) {
        QueryRewriteResult rewrite = queryRewriteService.rewrite(new QueryRewriteRequest(question));
        QueryExpansionResult expansion = queryExpansionService.expand(new QueryExpansionRequest(rewrite.rewrittenQuery()));
        List<String> retrievalQueries = retrievalQueries(rewrite.rewrittenQuery(), expansion);

        sink.emit(AdvisorEvent.of(AdvisorStage.QUERY_REFINED, "Query refined", Map.of(
                "query", rewrite.rewrittenQuery(),
                "rewriteStrategy", rewrite.rewriteStrategy(),
                "rewriteLatencyMs", rewrite.latencyMs(),
                "retrievalQueries", retrievalQueries
        )));
        return new QueryPlan(rewrite, expansion, retrievalQueries);
    }

    private List<String> retrievalQueries(String refinedQuery, QueryExpansionResult expansion) {
        List<String> retrievalQueries = expansion.generatedQueries().stream()
                .map(generated -> generated.query())
                .toList();
        if (!retrievalQueries.isEmpty()) {
            return retrievalQueries;
        }
        if (!refinedQuery.isBlank()) {
            return List.of(refinedQuery);
        }
        throw new IllegalArgumentException("Question must produce at least one retrieval query");
    }

    private RetrievalStep retrieve(
            String question,
            RetrievalFilters filters,
            QueryPlan plan,
            AdvisorEventSink sink
    ) {
        sink.emit(AdvisorEvent.of(AdvisorStage.VECTOR_SEARCH_STARTED, "Vector search started"));
        Instant started = Instant.now();
        HybridSearchResult retrieval = hybridRetrievalService.search(
                question,
                plan.refinedQuery(),
                plan.retrievalQueries(),
                retrievalTopK,
                filters
        );
        long latencyMs = elapsedMs(started);
        List<RetrievedChunk> chunks = rerankWithNeighbors(question, mergeRetrieved(retrieval.fusedResults()));

        LOGGER.info(
                "Advisor retrieval completed. plannedQueries={}, retrievedChunks={}",
                retrieval.expandedQueries().size(),
                chunks.size()
        );
        sink.emit(AdvisorEvent.of(AdvisorStage.CHUNKS_RETRIEVED, "Chunks retrieved", Map.of(
                "count", chunks.size(),
                "plannedQueries", retrieval.expandedQueries().size(),
                "latencyMs", latencyMs,
                "retrievalStatus", retrieval.status(),
                "retrievalStrategy", retrieval.retrievalStrategy(),
                "queryId", retrieval.queryId()
        )));
        return new RetrievalStep(retrieval, chunks, latencyMs);
    }

    private List<RetrievedChunk> rerankWithNeighbors(String question, List<RetrievedChunk> retrieved) {
        var seeds = retrieved.stream().limit(parentChildSeedLimit).toList();
        var neighbors = vectorSearchRepository.findActiveNeighbors(seeds);
        return reranker.rerank(question, mergeRetrieved(List.copyOf(concat(retrieved, neighbors)))).stream()
                .limit(rerankedLimit)
                .map(reranked -> reranked.toRetrievedChunk())
                .toList();
    }

    private ContextStep buildContext(String question, List<RetrievedChunk> retrieved, AdvisorEventSink sink) {
        sink.emit(AdvisorEvent.of(AdvisorStage.CONTEXT_FILTERING_STARTED, "Context filtering started"));
        Instant started = Instant.now();
        BuiltContext context = contextManager.build(question, retrieved);
        long latencyMs = elapsedMs(started);

        LOGGER.info(
                "Advisor context built. usedChunks={}, discardedChunks={}, estimatedTokens={}, documentDiversity={}",
                context.metrics().usedChunks(),
                context.metrics().discardedChunks(),
                context.metrics().estimatedTokens(),
                context.metrics().documentDiversity()
        );
        sink.emit(AdvisorEvent.of(AdvisorStage.CONTEXT_BUILT, "Context built", contextEventDetails(question, context, latencyMs)));
        return new ContextStep(context, latencyMs);
    }

    private Map<String, Object> contextEventDetails(String question, BuiltContext context, long latencyMs) {
        int questionTokens = tokenEstimator.estimate(question);
        var details = new LinkedHashMap<String, Object>();
        details.put("questionTokens", questionTokens);
        details.put("usedChunks", context.metrics().usedChunks());
        details.put("discardedChunks", context.metrics().discardedChunks());
        details.put("estimatedTokens", context.metrics().estimatedTokens());
        details.put("totalEstimatedInputTokens", questionTokens + context.metrics().estimatedTokens());
        details.put("documentDiversity", context.metrics().documentDiversity());
        details.put("duplicateDiscardedChunks", context.metrics().duplicateDiscardedChunks());
        details.put("nearDuplicateDiscardedChunks", context.metrics().nearDuplicateDiscardedChunks());
        details.put("documentQuotaDiscardedChunks", context.metrics().documentQuotaDiscardedChunks());
        details.put("tokenBudgetDiscardedChunks", context.metrics().tokenBudgetDiscardedChunks());
        details.put("maxChunkDiscardedChunks", context.metrics().maxChunkDiscardedChunks());
        details.put("latencyMs", latencyMs);
        return details;
    }

    private AnswerStep generateAnswer(String question, BuiltContext context, AdvisorEventSink sink) {
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
        return new AnswerStep(answer, verification, latencyMs);
    }

    private PredictionStep predictQuality(String question, List<RetrievedChunk> retrieved, BuiltContext context) {
        var features = features(question, retrieved, context.metrics().usedChunks(), context.metrics().documentDiversity());
        Instant started = Instant.now();
        RetrievalQualityPrediction prediction = qualityPredictor.predict(features);
        long latencyMs = elapsedMs(started);

        LOGGER.info(
                "Advisor retrieval quality predicted. label={}, probability={}, modelVersion={}",
                prediction.label(),
                prediction.probability(),
                prediction.modelVersion()
        );
        return new PredictionStep(prediction, latencyMs);
    }

    private TraceStep saveTrace(
            String question,
            Instant requestStarted,
            QueryPlan plan,
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
                new RetrievalTraceTimings(
                        retrieval.latencyMs(),
                        context.latencyMs(),
                        answer.latencyMs(),
                        prediction.latencyMs(),
                        elapsedMs(requestStarted)
                ),
                answerGenerator.name(),
                "HYBRID_MULTI_QUERY_RERANKED",
                String.join(" || ", plan.retrievalQueries()),
                plan.rewrite().latencyMs(),
                plan.rewrite().status(),
                answer.verification().verified(),
                answer.verification().reason()
        );
        return new TraceStep(traceId);
    }

    private AdvisorAnswer complete(
            String question,
            QueryPlan plan,
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

    private List<RetrievedChunk> mergeRetrieved(List<RetrievedChunk> chunks) {
        var byId = new LinkedHashMap<java.util.UUID, RetrievedChunk>();
        for (RetrievedChunk chunk : chunks) {
            byId.merge(chunk.chunkId(), chunk, (left, right) -> left.combinedScore() >= right.combinedScore() ? left : right);
        }
        return List.copyOf(byId.values());
    }

    private AdvisorAnswer answerWithoutRetrieval(
            String question,
            QueryRoute route,
            Instant requestStarted,
            AdvisorEventSink sink
    ) {
        var metrics = new ContextMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        var prediction = new RetrievalQualityPrediction(route.reason(), 1.0, "query-router");
        var traceId = traceRepository.save(
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
        LOGGER.info("Advisor request completed without retrieval. traceId={}, intent={}, reason={}",
                traceId, route.intent(), route.reason());
        return new AdvisorAnswer(
                traceId,
                question,
                question,
                route.response(),
                metrics,
                prediction,
                List.of()
        );
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

    private record QueryPlan(
            QueryRewriteResult rewrite,
            QueryExpansionResult expansion,
            List<String> retrievalQueries
    ) {
        String refinedQuery() {
            return rewrite.rewrittenQuery();
        }
    }

    private record RetrievalStep(
            HybridSearchResult result,
            List<RetrievedChunk> chunks,
            long latencyMs
    ) {
    }

    private record ContextStep(
            BuiltContext context,
            long latencyMs
    ) {
    }

    private record AnswerStep(
            String answer,
            AnswerVerification verification,
            long latencyMs
    ) {
    }

    private record PredictionStep(
            RetrievalQualityPrediction prediction,
            long latencyMs
    ) {
    }

    private record TraceStep(
            UUID traceId
    ) {
    }
}
