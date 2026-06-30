package com.shibajide.policyintelligence.chunking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentenceAwareChunkerTest {

    private final SentenceAwareChunker chunker = new SentenceAwareChunker();

    @Test
    void keepsSentenceBoundariesWhenPossible() {
        var chunks = chunker.chunk(
                "Contractors need approval. Production access is restricted. Audit evidence is required. Exceptions must be documented.",
                100,
                0
        );

        assertThat(chunks).hasSize(2);
        assertThat(chunks.getFirst()).endsWith(".");
        assertThat(chunks.getLast()).startsWith("Exceptions");
    }

    @Test
    void canCarryOverlapIntoNextChunk() {
        var chunks = chunker.chunk(
                "Alpha policy applies. Beta controls apply. Gamma evidence applies. Delta approval applies. "
                        + "Epsilon audit applies. Zeta monitoring applies.",
                100,
                12
        );

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(1)).contains("Epsilon audit applies.");
        assertThat(chunks.get(1)).doesNotStartWith("Epsilon");
    }
}
