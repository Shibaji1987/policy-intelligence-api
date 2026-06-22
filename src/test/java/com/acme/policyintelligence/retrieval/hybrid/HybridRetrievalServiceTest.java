package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.retrieval.fusion.ReciprocalRankFusionService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HybridRetrievalServiceTest {

    @Test
    void completesHybridRetrievalWhenBothRetrieversSucceed() {
        HybridRetrievalService service = new HybridRetrievalService(
                vectorService(List.of(chunk("vector"))),
                keywordService(List.of(chunk("keyword"))),
                new ReciprocalRankFusionService()
        );

        HybridSearchResult result = service.search("contractor access", 10, RetrievalFilters.defaults());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.trace().vectorStatus()).isEqualTo("SUCCESS");
        assertThat(result.trace().keywordStatus()).isEqualTo("SUCCESS");
        assertThat(result.trace().fusionStrategy()).isEqualTo("RRF");
        assertThat(result.trace().filtersApplied()).containsEntry("tenantId", "default");
        assertThat(result.trace().topKReturned()).isEqualTo(result.fusedResults().size());
    }

    @Test
    void degradesToKeywordOnlyWhenVectorFails() {
        HybridRetrievalService service = new HybridRetrievalService(
                failingVectorService(),
                keywordService(List.of(chunk("keyword"))),
                new ReciprocalRankFusionService()
        );

        HybridSearchResult result = service.search("contractor access", 10, RetrievalFilters.defaults());

        assertThat(result.status()).isEqualTo("DEGRADED");
        assertThat(result.retrievalStrategy()).isEqualTo("KEYWORD_ONLY_DEGRADED");
        assertThat(result.vectorResult().status()).isEqualTo(RetrievalStatus.FAILED);
        assertThat(result.vectorError()).isEqualTo("vector unavailable");
        assertThat(result.keywordResults()).hasSize(1);
        assertThat(result.fusedResults()).hasSize(1);
        assertThat(result.trace().fallbackUsed()).isTrue();
        assertThat(result.trace().warnings()).contains("VECTOR_RETRIEVER_FAILED");
    }

    @Test
    void degradesToVectorOnlyWhenKeywordFails() {
        HybridRetrievalService service = new HybridRetrievalService(
                vectorService(List.of(chunk("vector"))),
                failingKeywordService(),
                new ReciprocalRankFusionService()
        );

        HybridSearchResult result = service.search("contractor access", 10, RetrievalFilters.defaults());

        assertThat(result.status()).isEqualTo("DEGRADED");
        assertThat(result.retrievalStrategy()).isEqualTo("VECTOR_ONLY_DEGRADED");
        assertThat(result.keywordError()).isEqualTo("keyword unavailable");
        assertThat(result.fusedResults()).hasSize(1);
    }

    @Test
    void reportsFailedWhenBothRetrieversFail() {
        HybridRetrievalService service = new HybridRetrievalService(
                failingVectorService(),
                failingKeywordService(),
                new ReciprocalRankFusionService()
        );

        HybridSearchResult result = service.search("contractor access", 10, RetrievalFilters.defaults());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.retrievalStrategy()).isEqualTo("NO_RESULTS");
        assertThat(result.fusedResults()).isEmpty();
        assertThat(result.trace().warnings()).contains("VECTOR_RETRIEVER_FAILED", "KEYWORD_RETRIEVER_FAILED", "FUSION_EMPTY");
    }

    @Test
    void distinguishesEmptyResultsFromFailures() {
        HybridRetrievalService service = new HybridRetrievalService(
                vectorService(List.of()),
                keywordService(List.of()),
                new ReciprocalRankFusionService()
        );

        HybridSearchResult result = service.search("contractor access", 10, RetrievalFilters.defaults());

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.vectorResult().status()).isEqualTo(RetrievalStatus.EMPTY);
        assertThat(result.keywordResult().status()).isEqualTo(RetrievalStatus.EMPTY);
        assertThat(result.trace().warnings()).contains("VECTOR_RETRIEVER_EMPTY", "KEYWORD_RETRIEVER_EMPTY", "FUSION_EMPTY");
    }

    @Test
    void rejectsBlankQuery() {
        HybridRetrievalService service = new HybridRetrievalService(
                vectorService(List.of()),
                keywordService(List.of()),
                new ReciprocalRankFusionService()
        );

        assertThatThrownBy(() -> service.search(" ", 10, RetrievalFilters.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void validatesTopKBounds() {
        HybridRetrievalService service = new HybridRetrievalService(
                vectorService(List.of()),
                keywordService(List.of()),
                new ReciprocalRankFusionService()
        );

        assertThat(service.search("policy", 0, RetrievalFilters.defaults()).effectiveTopK()).isEqualTo(20);
        assertThat(service.search("policy", 500, RetrievalFilters.defaults()).effectiveTopK()).isEqualTo(100);
    }

    @Test
    void passesQueryVariantsToRetrievers() {
        var seenQueries = new ArrayList<String>();
        HybridRetrievalService service = new HybridRetrievalService(
                new VectorRetrievalService(null, null) {
                    @Override
                    public List<RetrievedChunk> retrieve(String query, int topK, RetrievalFilters filters) {
                        seenQueries.add(query);
                        return List.of(chunk(query));
                    }
                },
                keywordService(List.of()),
                new ReciprocalRankFusionService()
        );

        HybridSearchResult result = service.search("original", "rewritten", List.of("q1", "q2"), 10, RetrievalFilters.defaults());

        assertThat(seenQueries).containsExactly("q1", "q2");
        assertThat(result.expandedQueries()).containsExactly("q1", "q2");
        assertThat(result.fusedResults()).allSatisfy(chunk -> assertThat(chunk.matchedQueryVariant()).isIn("q1", "q2"));
    }

    private VectorRetrievalService vectorService(List<RetrievedChunk> chunks) {
        return new VectorRetrievalService(null, null) {
            @Override
            public List<RetrievedChunk> retrieve(String query, int topK, RetrievalFilters filters) {
                return chunks;
            }
        };
    }

    private VectorRetrievalService failingVectorService() {
        return new VectorRetrievalService(null, null) {
            @Override
            public List<RetrievedChunk> retrieve(String query, int topK, RetrievalFilters filters) {
                throw new IllegalStateException("vector unavailable");
            }
        };
    }

    private KeywordRetrievalService keywordService(List<RetrievedChunk> chunks) {
        return new KeywordRetrievalService(null) {
            @Override
            public List<RetrievedChunk> retrieve(String query, int topK, RetrievalFilters filters) {
                return chunks;
            }
        };
    }

    private KeywordRetrievalService failingKeywordService() {
        return new KeywordRetrievalService(null) {
            @Override
            public List<RetrievedChunk> retrieve(String query, int topK, RetrievalFilters filters) {
                throw new IllegalStateException("keyword unavailable");
            }
        };
    }

    private RetrievedChunk chunk(String title) {
        return new RetrievedChunk(
                UUID.randomUUID(),
                title,
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                0,
                "section-0",
                "Section 1",
                "Contractor access requires approval.",
                0.8,
                0.4,
                0.7,
                1,
                1,
                0,
                "BOTH",
                null,
                0,
                null,
                title,
                "TEST",
                "Contractor access requires approval."
        );
    }
}
