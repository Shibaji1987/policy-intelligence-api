package com.acme.policyintelligence.retrieval.rerank;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@Primary
@Component
public class HeuristicReranker implements Reranker {

    @Override
    public List<RerankedChunk> rerank(String question, List<RetrievedChunk> chunks) {
        List<RetrievedChunk> sorted = chunks.stream()
                .sorted(Comparator.comparingDouble(this::score).reversed())
                .toList();
        return IntStream.range(0, sorted.size())
                .mapToObj(index -> {
                    RetrievedChunk chunk = sorted.get(index);
                    return new RerankedChunk(chunk, chunk.rrfScore(), score(chunk), index + 1, reason(chunk));
                })
                .toList();
    }

    private double score(RetrievedChunk chunk) {
        return (0.60 * chunk.rrfScore())
                + (0.25 * Math.max(0, chunk.similarityScore()))
                + (0.15 * Math.max(0, chunk.keywordScore()));
    }

    private String reason(RetrievedChunk chunk) {
        if ("BOTH".equals(chunk.retrievalSource())) {
            return "High vector and keyword agreement";
        }
        if (chunk.vectorRank() != null) {
            return "Strong semantic match";
        }
        return "Strong keyword match";
    }
}
