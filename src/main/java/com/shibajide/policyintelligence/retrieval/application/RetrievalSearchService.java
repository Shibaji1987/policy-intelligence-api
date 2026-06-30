package com.shibajide.policyintelligence.retrieval.application;

import com.shibajide.policyintelligence.cache.application.RetrievalCache;
import com.shibajide.policyintelligence.billing.application.BillingEstimator;
import com.shibajide.policyintelligence.document.application.CorpusVersionService;
import com.shibajide.policyintelligence.embedding.application.EmbeddingGenerator;
import com.shibajide.policyintelligence.retrieval.infrastructure.VectorSearchRepository;
import com.shibajide.policyintelligence.shared.text.TokenEstimator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RetrievalSearchService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 50;

    private final EmbeddingGenerator embeddingGenerator;
    private final VectorSearchRepository vectorSearchRepository;
    private final CorpusVersionService corpusVersionService;
    private final RetrievalCache retrievalCache;
    private final TokenEstimator tokenEstimator;
    private final BillingEstimator billingEstimator;

    public RetrievalSearchService(
            EmbeddingGenerator embeddingGenerator,
            VectorSearchRepository vectorSearchRepository,
            CorpusVersionService corpusVersionService,
            RetrievalCache retrievalCache,
            TokenEstimator tokenEstimator,
            BillingEstimator billingEstimator
    ) {
        this.embeddingGenerator = embeddingGenerator;
        this.vectorSearchRepository = vectorSearchRepository;
        this.corpusVersionService = corpusVersionService;
        this.retrievalCache = retrievalCache;
        this.tokenEstimator = tokenEstimator;
        this.billingEstimator = billingEstimator;
    }

    public RetrievalSearchResponse search(String query, Integer topK) {
        return search(query, topK, RetrievalFilters.defaults());
    }

    public RetrievalSearchResponse search(String query, Integer topK, RetrievalFilters filters) {
        RetrievalSearchRequest request = request(query, topK, filters);
        var cached = retrievalCache.get(request.cacheKey());
        if (cached.isPresent()) {
            return cachedResponse(cached.get());
        }
        RetrievalSearchResponse response = searchUncached(request);
        retrievalCache.put(request.cacheKey(), response);
        return response;
    }

    private RetrievalSearchRequest request(String query, Integer topK, RetrievalFilters filters) {
        String effectiveQuery = RetrievalSupport.requireQuery(query, "Retrieval search");
        RetrievalFilters effectiveFilters = filters == null ? RetrievalFilters.defaults() : filters;
        int effectiveTopK = topK == null ? DEFAULT_TOP_K : Math.clamp(topK, 1, MAX_TOP_K);
        long corpusVersion = corpusVersion(effectiveFilters);
        return new RetrievalSearchRequest(
                effectiveQuery,
                effectiveTopK,
                effectiveFilters,
                corpusVersion,
                cacheKey(effectiveQuery, effectiveTopK, effectiveFilters, corpusVersion)
        );
    }

    private RetrievalSearchResponse searchUncached(RetrievalSearchRequest request) {
        var queryEmbedding = embeddingGenerator.embed(request.query());
        var chunks = vectorSearchRepository.search(
                queryEmbedding.values(),
                request.query(),
                request.topK(),
                request.filters()
        );
        return new RetrievalSearchResponse(
                request.query(),
                request.topK(),
                chunks.size(),
                queryEmbedding.model(),
                queryEmbedding.dimension(),
                request.corpusVersion(),
                false,
                tokenUsage(request.query(), chunks),
                chunks
        );
    }

    private RetrievalSearchResponse cachedResponse(RetrievalSearchResponse response) {
        return new RetrievalSearchResponse(
                response.query(),
                response.requestedTopK(),
                response.returnedChunks(),
                response.embeddingModel(),
                response.embeddingDimension(),
                response.corpusVersion(),
                true,
                response.tokenUsage(),
                response.chunks()
        );
    }

    private long corpusVersion(RetrievalFilters filters) {
        return corpusVersionService.currentVersion(filters.tenantId());
    }

    private String cacheKey(String query, int topK, RetrievalFilters filters, long corpusVersion) {
        return query.toLowerCase() + "|" + topK + "|" + corpusVersion + "|" + filters.cacheKey();
    }

    private RetrievalTokenUsage tokenUsage(String query, java.util.List<RetrievedChunk> chunks) {
        int queryTokens = tokenEstimator.estimate(query);
        int chunkTokens = chunks.stream()
                .mapToInt(chunk -> tokenEstimator.estimate(chunk.chunkText()))
                .sum();
        int excerptTokens = chunks.stream()
                .mapToInt(chunk -> tokenEstimator.estimate(chunk.excerpt()))
                .sum();
        return new RetrievalTokenUsage(
                queryTokens,
                chunkTokens,
                excerptTokens,
                queryTokens + chunkTokens,
                TokenEstimator.STRATEGY,
                billingEstimator.estimate(queryTokens + chunkTokens, 0)
        );
    }

    private record RetrievalSearchRequest(
            String query,
            int topK,
            RetrievalFilters filters,
            long corpusVersion,
            String cacheKey
    ) {
    }
}
