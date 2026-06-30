package com.shibajide.policyintelligence.context.application;

import com.shibajide.policyintelligence.context.compression.ContextCompressionService;
import com.shibajide.policyintelligence.context.packing.ContextPackingService;
import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;
import com.shibajide.policyintelligence.shared.text.TokenEstimator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContextManagerTest {

    @Test
    void rejectsDuplicateChunksWithTraceableDecision() {
        ContextManager manager = manager(5, 200, 5);
        RetrievedChunk first = chunk(UUID.randomUUID(), "Same policy text. Access requires approval.");
        RetrievedChunk duplicate = chunk(UUID.randomUUID(), "Same policy text. Access requires approval.");

        BuiltContext context = manager.build("approval", List.of(first, duplicate));

        assertThat(context.usedChunks()).containsExactly(first);
        assertThat(context.discardedChunks()).containsExactly(duplicate);
        assertThat(context.metrics().duplicateDiscardedChunks()).isEqualTo(1);
        assertThat(context.decisions()).extracting(ContextChunkDecision::reason)
                .containsExactly("USED", "DUPLICATE");
    }

    @Test
    void enforcesDocumentDiversityQuota() {
        ContextManager manager = manager(5, 200, 1);
        UUID documentId = UUID.randomUUID();
        RetrievedChunk first = chunk(documentId, "First unique production access requirement.");
        RetrievedChunk second = chunk(documentId, "Second unique audit logging requirement.");

        BuiltContext context = manager.build("production access", List.of(first, second));

        assertThat(context.usedChunks()).containsExactly(first);
        assertThat(context.discardedChunks()).containsExactly(second);
        assertThat(context.metrics().documentQuotaDiscardedChunks()).isEqualTo(1);
        assertThat(context.decisions()).extracting(ContextChunkDecision::reason)
                .containsExactly("USED", "DOCUMENT_DIVERSITY_LIMIT");
    }

    @Test
    void enforcesTokenBudgetAfterAcceptingEarlierChunks() {
        ContextManager manager = manager(5, 3, 5);
        RetrievedChunk first = chunk(UUID.randomUUID(), "short");
        RetrievedChunk overBudget = chunk(UUID.randomUUID(), "This second policy chunk is intentionally too long for the tiny budget.");

        BuiltContext context = manager.build("policy", List.of(first, overBudget));

        assertThat(context.usedChunks()).containsExactly(first);
        assertThat(context.discardedChunks()).containsExactly(overBudget);
        assertThat(context.metrics().tokenBudgetDiscardedChunks()).isEqualTo(1);
        assertThat(context.decisions()).extracting(ContextChunkDecision::reason)
                .containsExactly("USED", "TOKEN_BUDGET_EXCEEDED");
    }

    private ContextManager manager(int maxChunks, int tokenBudget, int maxChunksPerDocument) {
        return new ContextManager(
                maxChunks,
                tokenBudget,
                maxChunksPerDocument,
                new ContextCompressionService(),
                new ContextPackingService(),
                new TokenEstimator()
        );
    }

    private RetrievedChunk chunk(UUID documentId, String text) {
        return new RetrievedChunk(
                documentId,
                "Policy",
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                0,
                "section-0",
                "Section 1",
                text,
                0.8,
                0.1,
                0.8,
                1,
                null,
                0,
                "VECTOR",
                null,
                0,
                null,
                "policy",
                "TEST",
                text
        );
    }
}
