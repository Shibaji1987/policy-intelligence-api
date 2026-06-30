package com.shibajide.policyintelligence.context.compression;

import static org.assertj.core.api.Assertions.assertThat;

import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContextCompressionServiceTest {

    private final ContextCompressionService service = new ContextCompressionService();

    @Test
    void repeatedTokensDoNotFailCompression() {
        var chunk = new RetrievedChunk(
                UUID.randomUUID(),
                "Policy",
                UUID.randomUUID(),
                1,
                UUID.randomUUID(),
                0,
                "section-1",
                "Section 1",
                "The policy says the contractor may access the system only after the approval is recorded.",
                0.9,
                0.8,
                0.85,
                1,
                1,
                0.1,
                "BOTH",
                null,
                0,
                null,
                "Can the contractor access the system?",
                "HYBRID",
                "The policy says the contractor may access the system."
        );

        CompressedContext context = service.compress("Can the contractor access the system?", List.of(chunk));

        assertThat(context.chunks()).hasSize(1);
        assertThat(context.chunks().getFirst().compressedText()).contains("contractor");
    }
}
