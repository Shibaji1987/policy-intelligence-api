package com.shibajide.policyintelligence.chunking;

import com.shibajide.policyintelligence.document.domain.ChunkingStrategy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FixedSizeChunker implements Chunker {

    @Override
    public ChunkingStrategy strategy() {
        return ChunkingStrategy.FIXED_SIZE;
    }

    @Override
    public List<String> chunk(String text, int chunkSize, int overlap) {
        ChunkingArguments.validate(text, chunkSize, 0);
        var chunks = new ArrayList<String>();
        for (int start = 0; start < text.length(); start += chunkSize) {
            chunks.add(text.substring(start, Math.min(start + chunkSize, text.length())));
        }
        return List.copyOf(chunks);
    }
}
