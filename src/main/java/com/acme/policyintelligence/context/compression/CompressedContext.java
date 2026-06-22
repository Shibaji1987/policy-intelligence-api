package com.acme.policyintelligence.context.compression;

import java.util.List;

public record CompressedContext(
        List<CompressedChunk> chunks,
        List<CompressionDecision> decisions
) {
}
