package com.acme.policyintelligence.retrieval.application;

import com.acme.policyintelligence.cache.application.RetrievalCache;
import com.acme.policyintelligence.document.infrastructure.CorpusStateRepository;
import com.acme.policyintelligence.embedding.application.EmbeddingGenerator;
import com.acme.policyintelligence.retrieval.infrastructure.VectorSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RetrievalSearchService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;

    private final EmbeddingGenerator embeddingGenerator;
    private final VectorSearchRepository vectorSearchRepository;
    private final CorpusStateRepository corpusStateRepository;
    private final RetrievalCache retrievalCache;

    public RetrievalSearchService(
            EmbeddingGenerator embeddingGenerator,
            VectorSearchRepository vectorSearchRepository,
            CorpusStateRepository corpusStateRepository,
            RetrievalCache retrievalCache
    ) {
        this.embeddingGenerator = embeddingGenerator;
        this.vectorSearchRepository = vectorSearchRepository;
        this.corpusStateRepository = corpusStateRepository;
        this.retrievalCache = retrievalCache;
    }

    public RetrievalSearchResponse search(String query, Integer topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }
        int effectiveTopK = topK == null ? DEFAULT_TOP_K : Math.clamp(topK, 1, MAX_TOP_K);
        long corpusVersion = corpusStateRepository.findById((short) 1)
                .orElseThrow()
                .getCorpusVersion();
        String cacheKey = query.strip().toLowerCase() + "|" + effectiveTopK + "|" + corpusVersion;
        var cached = retrievalCache.get(cacheKey);
        if (cached.isPresent()) {
            var response = cached.get();
            return new RetrievalSearchResponse(
                    response.query(),
                    response.requestedTopK(),
                    response.returnedChunks(),
                    response.embeddingModel(),
                    response.embeddingDimension(),
                    true,
                    response.chunks()
            );
        }
        var queryEmbedding = embeddingGenerator.embed(query);
        var chunks = vectorSearchRepository.search(queryEmbedding.values(), effectiveTopK);
        var response = new RetrievalSearchResponse(
                query,
                effectiveTopK,
                chunks.size(),
                queryEmbedding.model(),
                queryEmbedding.dimension(),
                false,
                chunks
        );
        retrievalCache.put(cacheKey, response);
        return response;
    }
}
