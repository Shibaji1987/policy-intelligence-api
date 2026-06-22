package com.acme.policyintelligence.retrieval.fusion;

import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.retrieval.hybrid.RetrievalSource;
import com.acme.policyintelligence.retrieval.hybrid.RetrieverExecutionResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReciprocalRankFusionServiceTest {

    @Test
    void fusesVectorOnlyResults() {
        var service = service(60, new SimpleMeterRegistry());
        RetrievedChunk chunk = chunk("00000000-0000-0000-0000-000000000001", 0.9, 0.0);

        FusionResult result = service.fuse(List.of(chunk), List.of(), 5);

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().getFirst().retrievalSource()).isEqualTo("VECTOR");
        assertThat(result.results().getFirst().vectorRank()).isEqualTo(1);
        assertThat(result.results().getFirst().keywordRank()).isNull();
        assertThat(result.trace().vectorInputCount()).isEqualTo(1);
        assertThat(result.trace().keywordInputCount()).isZero();
    }

    @Test
    void fusesKeywordOnlyResults() {
        var service = service(60, new SimpleMeterRegistry());
        RetrievedChunk chunk = chunk("00000000-0000-0000-0000-000000000002", 0.0, 0.8);

        FusionResult result = service.fuse(List.of(), List.of(chunk), 5);

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().getFirst().retrievalSource()).isEqualTo("KEYWORD");
        assertThat(result.results().getFirst().vectorRank()).isNull();
        assertThat(result.results().getFirst().keywordRank()).isEqualTo(1);
    }

    @Test
    void mergesOverlappingChunkAndCalculatesRrfScore() {
        var service = service(60, new SimpleMeterRegistry());
        RetrievedChunk sharedVector = chunk("00000000-0000-0000-0000-000000000003", 0.91, 0.1);
        RetrievedChunk vectorOnly = chunk("00000000-0000-0000-0000-000000000004", 0.89, 0.0);
        RetrievedChunk keywordOnly = chunk("00000000-0000-0000-0000-000000000005", 0.0, 0.77);
        RetrievedChunk sharedKeyword = chunk("00000000-0000-0000-0000-000000000003", 0.0, 0.95);

        FusionResult result = service.fuse(List.of(sharedVector, vectorOnly), List.of(keywordOnly, sharedKeyword), 10);

        RetrievedChunk shared = result.results().stream()
                .filter(chunk -> chunk.chunkId().equals(sharedVector.chunkId()))
                .findFirst()
                .orElseThrow();
        assertThat(shared.retrievalSource()).isEqualTo("BOTH");
        assertThat(shared.vectorRank()).isEqualTo(1);
        assertThat(shared.keywordRank()).isEqualTo(2);
        assertThat(shared.rrfScore()).isEqualTo(1.0 / 61 + 1.0 / 62);
        assertThat(result.trace().duplicateCount()).isEqualTo(1);
        assertThat(result.trace().uniqueCandidateCount()).isEqualTo(3);
    }

    @Test
    void appliesLimitAndRecordsTraceAndMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        var service = service(60, registry);

        FusionResult result = service.fuse(
                List.of(
                        chunk("00000000-0000-0000-0000-000000000006", 0.9, 0),
                        chunk("00000000-0000-0000-0000-000000000007", 0.8, 0)
                ),
                List.of(chunk("00000000-0000-0000-0000-000000000008", 0, 0.8)),
                2
        );

        assertThat(result.results()).hasSize(2);
        assertThat(result.trace().algorithm()).isEqualTo("RECIPROCAL_RANK_FUSION");
        assertThat(result.trace().rrfK()).isEqualTo(60);
        assertThat(result.trace().outputCount()).isEqualTo(2);
        assertThat(registry.counter("rag.fusion.input.count").count()).isEqualTo(3);
        assertThat(registry.counter("rag.fusion.output.count").count()).isEqualTo(2);
    }

    @Test
    void supportsOutcomeRequestAndCarriesFilters() {
        var service = service(60, new SimpleMeterRegistry());
        UUID traceId = UUID.randomUUID();
        RetrievalFilters filters = new RetrievalFilters("tenant-a", "security", "us", "policy", "internal");
        var request = new FusionRequest(
                traceId,
                List.of(
                        RetrieverExecutionResult.success(
                                RetrievalSource.VECTOR,
                                List.of(chunk("00000000-0000-0000-0000-000000000009", 0.9, 0)),
                                3
                        ),
                        RetrieverExecutionResult.success(
                                RetrievalSource.KEYWORD,
                                List.of(chunk("00000000-0000-0000-0000-000000000010", 0, 0.8)),
                                2
                        )
                ),
                5,
                filters
        );

        FusionResult result = service.fuse(request);

        assertThat(result.trace().traceId()).isEqualTo(traceId);
        assertThat(result.trace().filtersApplied())
                .containsEntry("tenantId", "tenant-a")
                .containsEntry("department", "security");
    }

    @Test
    void rejectsNullInputsAndInvalidLimit() {
        var service = service(60, new SimpleMeterRegistry());
        var outcomesWithNull = new java.util.ArrayList<RetrieverExecutionResult>();
        outcomesWithNull.add(null);

        assertThatThrownBy(() -> service.fuse((FusionRequest) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request");
        assertThatThrownBy(() -> service.fuse((List<RetrieverExecutionResult>) null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outcomes");
        assertThatThrownBy(() -> service.fuse(new FusionRequest(UUID.randomUUID(), outcomesWithNull, 5, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null values");
        assertThatThrownBy(() -> service.fuse((List<RetrievedChunk>) null, List.of(), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vector results");
        assertThatThrownBy(() -> service.fuse(List.of(), null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Keyword results");
        assertThatThrownBy(() -> service.fuse(List.of(), List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void deterministicTieBreakerPrefersBothThenBestRankThenScoresThenChunkId() {
        var service = service(60, new SimpleMeterRegistry());
        RetrievedChunk bothVector = chunk("00000000-0000-0000-0000-000000000011", 0.7, 0);
        RetrievedChunk bothKeyword = chunk("00000000-0000-0000-0000-000000000011", 0, 0.7);
        RetrievedChunk vectorOnly = chunk("00000000-0000-0000-0000-000000000012", 0.99, 0);

        FusionResult result = service.fuse(List.of(bothVector, vectorOnly), List.of(bothKeyword), 10);

        assertThat(result.results().getFirst().chunkId()).isEqualTo(bothVector.chunkId());
        assertThat(result.results().getFirst().retrievalSource()).isEqualTo("BOTH");
    }

    @Test
    void emptyInputsReturnEmptyTraceableResult() {
        var service = service(60, new SimpleMeterRegistry());

        FusionResult result = service.fuse(List.of(), List.of(), 5);

        assertThat(result.results()).isEmpty();
        assertThat(result.trace().uniqueCandidateCount()).isZero();
        assertThat(result.trace().outputCount()).isZero();
    }

    private ReciprocalRankFusionService service(int rrfK, SimpleMeterRegistry registry) {
        return new ReciprocalRankFusionService(new FusionProperties(rrfK), registry);
    }

    private RetrievedChunk chunk(String chunkId, double similarityScore, double keywordScore) {
        return new RetrievedChunk(
                UUID.randomUUID(),
                "Policy",
                UUID.randomUUID(),
                1,
                UUID.fromString(chunkId),
                0,
                "section-0",
                "Section 1",
                "Policy text",
                similarityScore,
                keywordScore,
                Math.max(similarityScore, keywordScore),
                null,
                null,
                0,
                "TEST",
                null,
                0,
                null,
                "policy",
                "TEST",
                "Policy text"
        );
    }
}
