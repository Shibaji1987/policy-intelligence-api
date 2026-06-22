package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.retrieval.infrastructure.VectorSearchRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeywordRetrievalServiceTest {

    @Test
    void delegatesToRepositoryAndPreservesKeywordMetadata() {
        AtomicReference<String> seenQuery = new AtomicReference<>();
        AtomicReference<Integer> seenTopK = new AtomicReference<>();
        AtomicReference<RetrievalFilters> seenFilters = new AtomicReference<>();
        RetrievalFilters filters = new RetrievalFilters("tenant-a", "hr", "us", "policy", "internal");
        var service = service(new CapturingRepository(seenQuery, seenTopK, seenFilters, List.of(chunk())));

        KeywordRetrievalResult result = service.retrieveWithMetadata(" MFA policy ", 8, filters);

        assertThat(seenQuery.get()).isEqualTo("MFA policy");
        assertThat(seenTopK.get()).isEqualTo(8);
        assertThat(seenFilters.get()).isEqualTo(filters);
        assertThat(result.status()).isEqualTo(RetrievalStatus.SUCCESS);
        assertThat(result.searchMode()).isEqualTo("POSTGRES_FULL_TEXT");
        assertThat(result.rankingFunction()).isEqualTo("TS_RANK_CD");
        assertThat(result.language()).isEqualTo("english");
        assertThat(result.returnedChunkCount()).isEqualTo(1);
        assertThat(result.chunks().getFirst().keywordRank()).isEqualTo(1);
        assertThat(result.chunks().getFirst().keywordScore()).isEqualTo(0.72);
        assertThat(result.queryHash()).isNotBlank();
    }

    @Test
    void acceptsRequestObjectAndCustomSearchMode() {
        UUID traceId = UUID.randomUUID();
        var service = service(new CapturingRepository(new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>(), List.of()));
        var request = new KeywordRetrievalRequest(
                traceId,
                "policy exception",
                5,
                RetrievalFilters.defaults(),
                "POSTGRES_FULL_TEXT"
        );

        KeywordRetrievalResult result = service.retrieveWithMetadata(request);

        assertThat(result.traceId()).isEqualTo(traceId);
        assertThat(result.searchMode()).isEqualTo("POSTGRES_FULL_TEXT");
        assertThat(result.status()).isEqualTo(RetrievalStatus.EMPTY);
    }

    @Test
    void rejectsBlankQueryAndInvalidTopK() {
        var service = service(emptyRepository());

        assertThatThrownBy(() -> service.retrieveWithMetadata(" ", 5, RetrievalFilters.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> service.retrieveWithMetadata("policy", 0, RetrievalFilters.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK must be positive");
    }

    @Test
    void clampsLargeTopKAndDefaultsNullFilters() {
        AtomicReference<Integer> seenTopK = new AtomicReference<>();
        AtomicReference<RetrievalFilters> seenFilters = new AtomicReference<>();
        var service = service(new CapturingRepository(new AtomicReference<>(), seenTopK, seenFilters, List.of()));

        KeywordRetrievalResult result = service.retrieveWithMetadata("policy", 500, null);

        assertThat(seenTopK.get()).isEqualTo(100);
        assertThat(seenFilters.get()).isEqualTo(RetrievalFilters.defaults());
        assertThat(result.filters()).isEqualTo(RetrievalFilters.defaults());
        assertThat(result.requestedTopK()).isEqualTo(100);
    }

    @Test
    void handlesRepositoryFailureAndRetrieveWrapperThrows() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        var service = service(new VectorSearchRepository(null, null) {
            @Override
            public List<RetrievedChunk> keywordSearch(String query, int topK, RetrievalFilters filters) {
                throw new IllegalStateException("keyword index unavailable");
            }
        }, registry);

        KeywordRetrievalResult result = service.retrieveWithMetadata("policy", 5, RetrievalFilters.defaults());

        assertThat(result.status()).isEqualTo(RetrievalStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("keyword index unavailable");
        assertThat(registry.counter("rag.keyword.failure.count").count()).isEqualTo(1);
        assertThatThrownBy(() -> service.retrieve("policy", 5, RetrievalFilters.defaults()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keyword index unavailable");
    }

    @Test
    void recordsEmptyAndResultMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        var service = service(emptyRepository(), registry);

        KeywordRetrievalResult result = service.retrieveWithMetadata("policy", 5, RetrievalFilters.defaults());

        assertThat(result.status()).isEqualTo(RetrievalStatus.EMPTY);
        assertThat(registry.counter("rag.keyword.empty.count").count()).isEqualTo(1);
        assertThat(registry.counter("rag.keyword.result.count").count()).isEqualTo(0);
    }

    private KeywordRetrievalService service(VectorSearchRepository repository) {
        return service(repository, new SimpleMeterRegistry());
    }

    private KeywordRetrievalService service(VectorSearchRepository repository, SimpleMeterRegistry registry) {
        return new KeywordRetrievalService(repository, registry);
    }

    private VectorSearchRepository emptyRepository() {
        return new CapturingRepository(
                new AtomicReference<>(),
                new AtomicReference<>(),
                new AtomicReference<>(),
                List.of()
        );
    }

    private RetrievedChunk chunk() {
        return new RetrievedChunk(
                UUID.randomUUID(),
                "MFA Access Policy",
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                3,
                "section-0",
                "Section 1",
                "MFA is required for privileged access.",
                0,
                0.72,
                0.72,
                null,
                1,
                0,
                "KEYWORD",
                null,
                0,
                null,
                "MFA policy",
                "KEYWORD",
                "MFA is required for privileged access."
        );
    }

    private static class CapturingRepository extends VectorSearchRepository {

        private final AtomicReference<String> seenQuery;
        private final AtomicReference<Integer> seenTopK;
        private final AtomicReference<RetrievalFilters> seenFilters;
        private final List<RetrievedChunk> chunks;

        CapturingRepository(
                AtomicReference<String> seenQuery,
                AtomicReference<Integer> seenTopK,
                AtomicReference<RetrievalFilters> seenFilters,
                List<RetrievedChunk> chunks
        ) {
            super(null, null);
            this.seenQuery = seenQuery;
            this.seenTopK = seenTopK;
            this.seenFilters = seenFilters;
            this.chunks = chunks;
        }

        @Override
        public List<RetrievedChunk> keywordSearch(String query, int topK, RetrievalFilters filters) {
            seenQuery.set(query);
            seenTopK.set(topK);
            seenFilters.set(filters);
            return chunks;
        }
    }
}
