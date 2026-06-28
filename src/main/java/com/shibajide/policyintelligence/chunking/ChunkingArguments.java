package com.shibajide.policyintelligence.chunking;

final class ChunkingArguments {

    private ChunkingArguments() {
    }

    static void validate(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Document text must not be blank");
        }
        if (chunkSize < 100 || chunkSize > 20_000) {
            throw new IllegalArgumentException("Chunk size must be between 100 and 20000 characters");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("Chunk overlap must be non-negative and smaller than chunk size");
        }
    }
}
