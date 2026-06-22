package com.acme.policyintelligence.context.compression;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ContextCompressionService {

    public CompressedContext compress(String question, List<RetrievedChunk> chunks) {
        List<CompressedChunk> compressed = chunks.stream()
                .map(chunk -> compressChunk(question, chunk))
                .toList();
        List<CompressionDecision> decisions = compressed.stream()
                .map(chunk -> new CompressionDecision(
                        chunk.source().chunkId(),
                        chunk.originalTokenCount(),
                        chunk.compressedTokenCount(),
                        chunk.compressionRatio(),
                        chunk.compressionMethod()
                ))
                .toList();
        return new CompressedContext(compressed, decisions);
    }

    private CompressedChunk compressChunk(String question, RetrievedChunk chunk) {
        int originalTokens = estimateTokens(chunk.chunkText());
        Set<String> questionTokens = tokenSet(question);
        var kept = new LinkedHashSet<String>();
        for (String sentence : chunk.chunkText().split("(?<=[.!?])\\s+")) {
            String cleaned = sentence.strip();
            if (cleaned.isBlank() || isBoilerplate(cleaned)) {
                continue;
            }
            if (overlap(questionTokens, tokenSet(cleaned)) > 0 || kept.size() < 2) {
                kept.add(cleaned);
            }
        }
        String compressedText = kept.isEmpty() ? chunk.excerpt() : String.join(" ", kept);
        int compressedTokens = estimateTokens(compressedText);
        double ratio = originalTokens == 0 ? 1 : (double) compressedTokens / originalTokens;
        return new CompressedChunk(chunk, chunk.chunkText(), compressedText, originalTokens, compressedTokens, ratio, "EXTRACTIVE_SENTENCE_FILTER");
    }

    private boolean isBoilerplate(String sentence) {
        String normalized = sentence.toLowerCase(Locale.ROOT);
        return normalized.contains("table of contents") || normalized.contains("copyright") || normalized.contains("all rights reserved");
    }

    private int estimateTokens(String text) {
        return Math.max(1, (int) Math.ceil((text == null ? 0 : text.length()) / 4.0));
    }

    private int overlap(Set<String> first, Set<String> second) {
        int matches = 0;
        for (String token : first) {
            if (second.contains(token)) {
                matches++;
            }
        }
        return matches;
    }

    private Set<String> tokenSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Set.of(value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .strip()
                .split("\\s+"));
    }
}
