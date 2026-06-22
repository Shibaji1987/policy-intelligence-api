package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.embedding.application.EmbeddingGenerator;
import com.acme.policyintelligence.embedding.application.EmbeddingVector;
import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.retrieval.infrastructure.VectorSearchRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorRetrievalServiceTest {

    @Test
    void validQueryEmbedsOnceAndPassesVectorQueryTopKAndFiltersToRepository() {
        AtomicInteger embedCalls = new AtomicInteger();
        AtomicReference<float[]> seenVector = new AtomicReference<>();
        AtomicReference<String> seenQuery = new AtomicReference<>();
        AtomicReference<Integer> seenTopK = new AtomicReference<>();
        AtomicReference<RetrievalFilters> seenFilters = new AtomicReference<>();
        float[] vector = new float[] {0.1f, 0.2f, 0.3f};
        RetrievalFilters filters = new RetrievalFilters("tenant-a", "security", "us", "policy", "internal");
        var service = service(
                text -> {
                    embedCalls.incrementAndGet();
                    return new EmbeddingVector("test-model-v1", 3, vector);
                },
                new CapturingRepository(seenVector, seenQuery, seenTopK, seenFilters, List.of(chunk()))
        );

        VectorRetrievalResult result = service.retrieveWithMetadata(" contractor access ", 7, filters);

        assertThat(embedCalls).hasValue(1);
        assertThat(seenVector.get()).isSameAs(vector);
        assertThat(seenQuery.get()).isEqualTo("contractor access");
        assertThat(seenTopK.get()).isEqualTo(7);
        assertThat(seenFilters.get()).isEqualTo(filters);
        assertThat(result.status()).isEqualTo(RetrievalStatus.SUCCESS);
        assertThat(result.embeddingModel()).isEqualTo("test-model-v1");
        assertThat(result.embeddingDimension()).isEqualTo(3);
        assertThat(result.similarityMetric()).isEqualTo("COSINE");
        assertThat(result.returnedChunkCount()).isEqualTo(1);
        assertThat(result.queryHash()).isNotBlank();
    }

    @Test
    void rejectsBlankQueryAndNonPositiveTopK() {
        var service = service(text -> new EmbeddingVector("test", 3, new float[] {1, 2, 3}), emptyRepository());

        assertThatThrownBy(() -> service.retrieveWithMetadata(" ", 5, RetrievalFilters.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> service.retrieveWithMetadata("policy", 0, RetrievalFilters.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topK must be positive");
    }

    @Test
    void returnsFailureMetadataWhenEmbeddingFails() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        var service = service(text -> {
            throw new IllegalStateException("embedding unavailable");
        }, emptyRepository(), registry);

        VectorRetrievalResult result = service.retrieveWithMetadata("policy", 5, RetrievalFilters.defaults());

        assertThat(result.status()).isEqualTo(RetrievalStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("embedding unavailable");
        assertThat(registry.counter("rag.vector.embedding.failure.count").count()).isEqualTo(1);
        assertThat(registry.counter("rag.vector.failure.count").count()).isEqualTo(1);
    }

    @Test
    void returnsFailureMetadataWhenRepositoryFails() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        var service = service(
                text -> new EmbeddingVector("test", 3, new float[] {1, 2, 3}),
                new VectorSearchRepository(null, null) {
                    @Override
                    public List<RetrievedChunk> vectorSearch(
                            float[] queryEmbedding,
                            String query,
                            int topK,
                            RetrievalFilters filters
                    ) {
                        throw new IllegalStateException("pgvector unavailable");
                    }
                },
                registry
        );

        VectorRetrievalResult result = service.retrieveWithMetadata("policy", 5, RetrievalFilters.defaults());

        assertThat(result.status()).isEqualTo(RetrievalStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("pgvector unavailable");
        assertThat(registry.counter("rag.vector.pgvector.failure.count").count()).isEqualTo(1);
        assertThat(registry.counter("rag.vector.failure.count").count()).isEqualTo(1);
    }

    @Test
    void reportsEmptyResultsAndDefaultFilters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicReference<RetrievalFilters> seenFilters = new AtomicReference<>();
        var service = service(
                text -> new EmbeddingVector("test", 3, new float[] {1, 2, 3}),
                new CapturingRepository(new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>(), seenFilters, List.of()),
                registry
        );

        VectorRetrievalResult result = service.retrieveWithMetadata("policy", 5, null);

        assertThat(result.status()).isEqualTo(RetrievalStatus.EMPTY);
        assertThat(result.filters()).isEqualTo(RetrievalFilters.defaults());
        assertThat(seenFilters.get()).isEqualTo(RetrievalFilters.defaults());
        assertThat(registry.counter("rag.vector.empty.count").count()).isEqualTo(1);
    }

    @Test
    void emptyEmbeddingVectorIsAControlledFailureAndRetrieveWrapperThrows() {
        var service = service(text -> new EmbeddingVector("test", 0, new float[] {}), emptyRepository());

        VectorRetrievalResult result = service.retrieveWithMetadata("policy", 5, RetrievalFilters.defaults());

        assertThat(result.status()).isEqualTo(RetrievalStatus.FAILED);
        assertThat(result.failureReason()).contains("empty vector");
        assertThatThrownBy(() -> service.retrieve("policy", 5, RetrievalFilters.defaults()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty vector");
    }

    private VectorRetrievalService service(EmbeddingGenerator generator, VectorSearchRepository repository) {
        return service(generator, repository, new SimpleMeterRegistry());
    }

    private VectorRetrievalService service(
            EmbeddingGenerator generator,
            VectorSearchRepository repository,
            SimpleMeterRegistry registry
    ) {
        return new VectorRetrievalService(generator, repository, registry);
    }

    private VectorSearchRepository emptyRepository() {
        return new CapturingRepository(
                new AtomicReference<>(),
                new AtomicReference<>(),
                new AtomicReference<>(),
                new AtomicReference<>(),
                List.of()
        );
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
                "Contractor access requires approval.",
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
                "Contractor access requires approval."
        );
    }

    private static class CapturingRepository extends VectorSearchRepository {

        private final AtomicReference<float[]> seenVector;
        private final AtomicReference<String> seenQuery;
        private final AtomicReference<Integer> seenTopK;
        private final AtomicReference<RetrievalFilters> seenFilters;
        private final List<RetrievedChunk> chunks;

        CapturingRepository(
                AtomicReference<float[]> seenVector,
                AtomicReference<String> seenQuery,
                AtomicReference<Integer> seenTopK,
                AtomicReference<RetrievalFilters> seenFilters,
                List<RetrievedChunk> chunks
        ) {
            super(null, null);
            this.seenVector = seenVector;
            this.seenQuery = seenQuery;
            this.seenTopK = seenTopK;
            this.seenFilters = seenFilters;
            this.chunks = chunks;
        }

        @Override
        public List<RetrievedChunk> vectorSearch(float[] queryEmbedding, String query, int topK, RetrievalFilters filters) {
            seenVector.set(queryEmbedding);
            seenQuery.set(query);
            seenTopK.set(topK);
            seenFilters.set(filters);
            return chunks;
        }
    }
}
