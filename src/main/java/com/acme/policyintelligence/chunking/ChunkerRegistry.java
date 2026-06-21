package com.acme.policyintelligence.chunking;

import com.acme.policyintelligence.document.domain.ChunkingStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ChunkerRegistry {

    private final Map<ChunkingStrategy, Chunker> chunkers;

    public ChunkerRegistry(List<Chunker> chunkers) {
        var registered = new EnumMap<ChunkingStrategy, Chunker>(ChunkingStrategy.class);
        chunkers.forEach(chunker -> registered.put(chunker.strategy(), chunker));
        this.chunkers = Map.copyOf(registered);
    }

    public Chunker get(ChunkingStrategy strategy) {
        var chunker = chunkers.get(strategy);
        if (chunker == null) {
            throw new IllegalArgumentException("Unsupported chunking strategy: " + strategy);
        }
        return chunker;
    }
}
