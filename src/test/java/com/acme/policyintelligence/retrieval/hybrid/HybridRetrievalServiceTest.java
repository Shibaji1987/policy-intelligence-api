package com.acme.policyintelligence.retrieval.hybrid;

import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.retrieval.fusion.ReciprocalRankFusionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HybridRetrievalServiceTest {

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
        assertThat(result.vectorError()).isEqualTo("vector unavailable");
        assertThat(result.keywordResults()).hasSize(1);
        assertThat(result.fusedResults()).hasSize(1);
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
    void clampsInvalidTopKToDefault() {
        HybridRetrievalService service = new HybridRetrievalService(
                vectorService(List.of()),
                keywordService(List.of()),
                new ReciprocalRankFusionService()
        );

        HybridSearchResult result = service.search("policy", 0, RetrievalFilters.defaults());

        assertThat(result.requestedTopK()).isZero();
        assertThat(result.effectiveTopK()).isEqualTo(20);
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
                "TEST",
                "Contractor access requires approval."
        );
    }
}
