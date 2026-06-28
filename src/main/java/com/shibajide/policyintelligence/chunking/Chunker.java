package com.shibajide.policyintelligence.chunking;

import com.shibajide.policyintelligence.document.domain.ChunkingStrategy;

import java.util.List;

public interface Chunker {

    ChunkingStrategy strategy();

    List<String> chunk(String text, int chunkSize, int overlap);
}
