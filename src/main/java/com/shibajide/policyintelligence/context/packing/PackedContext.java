package com.shibajide.policyintelligence.context.packing;

import com.shibajide.policyintelligence.context.compression.CompressedChunk;

import java.util.List;

public record PackedContext(
        List<CompressedChunk> orderedChunks,
        LostInMiddleMitigationStrategy strategy
) {
}
