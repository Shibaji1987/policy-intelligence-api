package com.acme.policyintelligence.chunking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlidingWindowChunkerTest {

    private final SlidingWindowChunker chunker = new SlidingWindowChunker();

    @Test
    void splitsTextWithConfiguredOverlap() {
        String text = "a".repeat(80) + "b".repeat(20) + "c".repeat(80);

        var chunks = chunker.chunk(text, 100, 20);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).isEqualTo("a".repeat(80) + "b".repeat(20));
        assertThat(chunks.get(1)).isEqualTo("b".repeat(20) + "c".repeat(80));
    }

    @Test
    void rejectsOverlapEqualToChunkSize() {
        assertThatThrownBy(() -> chunker.chunk("a".repeat(200), 100, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("smaller than chunk size");
    }
}
