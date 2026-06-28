package com.shibajide.policyintelligence.context.compression;

import java.util.UUID;

public record CompressionDecision(
        UUID chunkId,
        int originalTokenCount,
        int compressedTokenCount,
        double compressionRatio,
        String compressionMethod
) {
}
