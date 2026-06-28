package com.shibajide.policyintelligence.retrieval.application;

import com.shibajide.policyintelligence.cache.application.RetrievalCache;
import com.shibajide.policyintelligence.billing.application.BillingEstimator;
import com.shibajide.policyintelligence.billing.application.BillingProperties;
import com.shibajide.policyintelligence.document.domain.CorpusState;
import com.shibajide.policyintelligence.document.infrastructure.CorpusStateRepository;
import com.shibajide.policyintelligence.embedding.application.EmbeddingVector;
import com.shibajide.policyintelligence.retrieval.infrastructure.VectorSearchRepository;
import com.shibajide.policyintelligence.shared.text.TokenEstimator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrievalSearchServiceTest {

    @Test
    void reportsTokenUsageForSearchResultsAndCacheHits() {
        AtomicInteger embedCalls = new AtomicInteger();
        TokenEstimator tokenEstimator = new TokenEstimator();
        RetrievedChunk chunk = chunk();
        var service = new RetrievalSearchService(
                query -> {
                    embedCalls.incrementAndGet();
                    return new EmbeddingVector("test-embedding", 3, new float[] {0.1f, 0.2f, 0.3f});
                },
                repository(List.of(chunk)),
                corpusStateRepository(),
                new RetrievalCache(),
                tokenEstimator,
                new BillingEstimator(new BillingProperties(
                        "ESTIMATED_INPUT_ONLY",
                        "USD",
                        BigDecimal.valueOf(2),
                        BigDecimal.valueOf(8)
                ))
        );

        RetrievalSearchResponse fresh = service.search("contractor access", 5);
        RetrievalSearchResponse cached = service.search("contractor access", 5);

        assertThat(fresh.cacheHit()).isFalse();
        assertThat(cached.cacheHit()).isTrue();
        assertThat(embedCalls).hasValue(1);
        assertThat(fresh.tokenUsage().queryTokens()).isEqualTo(tokenEstimator.estimate("contractor access"));
        assertThat(fresh.tokenUsage().returnedChunkTokens()).isEqualTo(tokenEstimator.estimate(chunk.chunkText()));
        assertThat(fresh.tokenUsage().returnedExcerptTokens()).isEqualTo(tokenEstimator.estimate(chunk.excerpt()));
        assertThat(fresh.tokenUsage().totalEstimatedTokens())
                .isEqualTo(tokenEstimator.estimate("contractor access") + tokenEstimator.estimate(chunk.chunkText()));
        assertThat(fresh.tokenUsage().tokenizationStrategy()).isEqualTo(TokenEstimator.STRATEGY);
        assertThat(fresh.tokenUsage().billingEstimate().strategy()).isEqualTo("ESTIMATED_INPUT_ONLY");
        assertThat(fresh.tokenUsage().billingEstimate().billableInputTokens())
                .isEqualTo(fresh.tokenUsage().totalEstimatedTokens());
        assertThat(fresh.tokenUsage().billingEstimate().billableOutputTokens()).isZero();
        assertThat(fresh.tokenUsage().billingEstimate().totalCost()).isEqualByComparingTo(
                BigDecimal.valueOf(fresh.tokenUsage().totalEstimatedTokens())
                        .multiply(BigDecimal.valueOf(2))
                        .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP)
        );
        assertThat(cached.tokenUsage()).isEqualTo(fresh.tokenUsage());
    }

    private VectorSearchRepository repository(List<RetrievedChunk> chunks) {
        return new VectorSearchRepository(null, null) {
            @Override
            public List<RetrievedChunk> search(
                    float[] queryEmbedding,
                    String query,
                    int topK,
                    RetrievalFilters filters
            ) {
                return chunks;
            }
        };
    }

    private CorpusStateRepository corpusStateRepository() {
        CorpusState state = mock(CorpusState.class);
        when(state.getCorpusVersion()).thenReturn(42L);
        CorpusStateRepository repository = mock(CorpusStateRepository.class);
        when(repository.findById((short) 1)).thenReturn(Optional.of(state));
        return repository;
    }

    private RetrievedChunk chunk() {
        return new RetrievedChunk(
                UUID.randomUUID(),
                "Production Data Access Policy",
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                0,
                "section-0",
                "Section 1",
                "Contractor access requires explicit approval.",
                0.81,
                0,
                0.81,
                1,
                null,
                0,
                "VECTOR",
                null,
                0,
                null,
                "contractor access",
                "VECTOR",
                "Requires approval."
        );
    }
}
