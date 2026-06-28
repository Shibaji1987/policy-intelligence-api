package com.shibajide.policyintelligence.chunking;

import com.shibajide.policyintelligence.document.domain.ChunkingStrategy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SlidingWindowChunker implements Chunker {

    @Override
    public ChunkingStrategy strategy() {
        return ChunkingStrategy.SLIDING_WINDOW;
    }

    @Override
    public List<String> chunk(String text, int chunkSize, int overlap) {
        ChunkingArguments.validate(text, chunkSize, overlap);
        var chunks = new ArrayList<String>();
        int step = chunkSize - overlap;
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
        }
        return List.copyOf(chunks);
    }
}
