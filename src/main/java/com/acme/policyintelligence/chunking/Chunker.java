package com.acme.policyintelligence.chunking;

import com.acme.policyintelligence.document.domain.ChunkingStrategy;

import java.util.List;

public interface Chunker {

    ChunkingStrategy strategy();

    List<String> chunk(String text, int chunkSize, int overlap);
}
