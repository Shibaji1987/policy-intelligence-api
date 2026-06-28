package com.shibajide.policyintelligence.context.compression;

import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;

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
