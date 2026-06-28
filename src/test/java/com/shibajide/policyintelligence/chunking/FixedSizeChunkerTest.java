package com.shibajide.policyintelligence.chunking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedSizeChunkerTest {

    private final FixedSizeChunker chunker = new FixedSizeChunker();

    @Test
    void splitsTextIntoNonOverlappingChunks() {
        String text = "a".repeat(250);

        var chunks = chunker.chunk(text, 100, 0);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(String::length).containsExactly(100, 100, 50);
        assertThat(String.join("", chunks)).isEqualTo(text);
    }
}
