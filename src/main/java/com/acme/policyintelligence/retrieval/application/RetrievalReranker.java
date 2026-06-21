package com.acme.policyintelligence.retrieval.application;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class RetrievalReranker {

    public List<RetrievedChunk> rerank(String question, List<RetrievedChunk> chunks, int limit) {
        Set<String> questionTokens = tokenSet(question);
        var bestByChunk = new LinkedHashMap<UUID, RetrievedChunk>();
        for (RetrievedChunk chunk : chunks) {
            bestByChunk.merge(chunk.chunkId(), chunk, this::best);
        }
        return bestByChunk.values().stream()
                .sorted(Comparator.comparingDouble((RetrievedChunk chunk) -> rerankScore(questionTokens, chunk)).reversed())
                .limit(limit)
                .toList();
    }

    private RetrievedChunk best(RetrievedChunk left, RetrievedChunk right) {
        return left.combinedScore() >= right.combinedScore() ? left : right;
    }

    private double rerankScore(Set<String> questionTokens, RetrievedChunk chunk) {
        double lexicalOverlap = overlap(questionTokens, tokenSet(chunk.chunkText()));
        double neighborBoost = "PARENT_CHILD_NEIGHBOR".equals(chunk.retrievalStrategy()) ? -0.03 : 0;
        return (chunk.combinedScore() * 0.72) + (chunk.similarityScore() * 0.18) + (lexicalOverlap * 0.10) + neighborBoost;
    }

    private double overlap(Set<String> questionTokens, Set<String> chunkTokens) {
        if (questionTokens.isEmpty() || chunkTokens.isEmpty()) {
            return 0;
        }
        int matches = 0;
        for (String token : questionTokens) {
            if (chunkTokens.contains(token)) {
                matches++;
            }
        }
        return (double) matches / questionTokens.size();
    }

    private Set<String> tokenSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (normalized.isBlank()) {
            return Set.of();
        }
        var tokens = new HashSet<String>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() > 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
