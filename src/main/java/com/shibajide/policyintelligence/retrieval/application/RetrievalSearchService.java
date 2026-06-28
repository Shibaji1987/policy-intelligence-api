package com.shibajide.policyintelligence.retrieval.application;

import com.shibajide.policyintelligence.cache.application.RetrievalCache;
import com.shibajide.policyintelligence.billing.application.BillingEstimator;
import com.shibajide.policyintelligence.document.infrastructure.CorpusStateRepository;
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
    private final CorpusStateRepository corpusStateRepository;
    private final RetrievalCache retrievalCache;
    private final TokenEstimator tokenEstimator;
    private final BillingEstimator billingEstimator;

    public RetrievalSearchService(
            EmbeddingGenerator embeddingGenerator,
            VectorSearchRepository vectorSearchRepository,
            CorpusStateRepository corpusStateRepository,
            RetrievalCache retrievalCache,
            TokenEstimator tokenEstimator,
            BillingEstimator billingEstimator
    ) {
        this.embeddingGenerator = embeddingGenerator;
        this.vectorSearchRepository = vectorSearchRepository;
        this.corpusStateRepository = corpusStateRepository;
        this.retrievalCache = retrievalCache;
        this.tokenEstimator = tokenEstimator;
        this.billingEstimator = billingEstimator;
    }

    public RetrievalSearchResponse search(String query, Integer topK) {
        return search(query, topK, RetrievalFilters.defaults());
    }

    public RetrievalSearchResponse search(String query, Integer topK, RetrievalFilters filters) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }
        RetrievalFilters effectiveFilters = filters == null ? RetrievalFilters.defaults() : filters;
        int effectiveTopK = topK == null ? DEFAULT_TOP_K : Math.clamp(topK, 1, MAX_TOP_K);
        long corpusVersion = corpusStateRepository.findById((short) 1)
                .orElseThrow()
                .getCorpusVersion();
        String cacheKey = query.strip().toLowerCase() + "|" + effectiveTopK + "|" + corpusVersion + "|" + effectiveFilters.cacheKey();
        var cached = retrievalCache.get(cacheKey);
        if (cached.isPresent()) {
            var response = cached.get();
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
        var queryEmbedding = embeddingGenerator.embed(query);
        var chunks = vectorSearchRepository.search(queryEmbedding.values(), query, effectiveTopK, effectiveFilters);
        var response = new RetrievalSearchResponse(
                query,
                effectiveTopK,
                chunks.size(),
                queryEmbedding.model(),
                queryEmbedding.dimension(),
                corpusVersion,
                false,
                tokenUsage(query, chunks),
                chunks
        );
        retrievalCache.put(cacheKey, response);
        return response;
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
}
