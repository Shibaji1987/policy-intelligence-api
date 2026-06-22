package com.acme.policyintelligence.context.compression;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;

public record CompressedChunk(
        RetrievedChunk source,
        String originalText,
        String compressedText,
        int originalTokenCount,
        int compressedTokenCount,
        double compressionRatio,
        String compressionMethod
) {
}
