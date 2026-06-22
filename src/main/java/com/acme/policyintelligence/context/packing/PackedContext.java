package com.acme.policyintelligence.context.packing;

import com.acme.policyintelligence.context.compression.CompressedChunk;

import java.util.List;

public record PackedContext(
        List<CompressedChunk> orderedChunks,
        LostInMiddleMitigationStrategy strategy
) {
}
