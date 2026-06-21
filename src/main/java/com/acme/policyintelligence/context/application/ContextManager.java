package com.acme.policyintelligence.context.application;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

@Service
public class ContextManager {

    private final int maxChunks;
    private final int tokenBudget;

    public ContextManager(
            @Value("${app.advisor.max-context-chunks:8}") int maxChunks,
            @Value("${app.advisor.context-token-budget:1800}") int tokenBudget
    ) {
        this.maxChunks = maxChunks;
        this.tokenBudget = tokenBudget;
    }

    public BuiltContext build(List<RetrievedChunk> retrievedChunks) {
        var used = new ArrayList<RetrievedChunk>();
        var discarded = new ArrayList<RetrievedChunk>();
        var fingerprints = new HashSet<String>();
        var documents = new HashSet<String>();
        int estimatedTokens = 0;

        for (RetrievedChunk chunk : retrievedChunks) {
            int chunkTokens = estimateTokens(chunk.chunkText());
            String fingerprint = fingerprint(chunk.chunkText());
            boolean duplicate = !fingerprints.add(fingerprint);
            boolean overBudget = estimatedTokens + chunkTokens > tokenBudget;
            boolean tooMany = used.size() >= maxChunks;

            if (duplicate || overBudget || tooMany) {
                discarded.add(chunk);
                continue;
            }

            used.add(chunk);
            documents.add(chunk.documentId().toString());
            estimatedTokens += chunkTokens;
        }

        String contextText = formatContext(used);
        var metrics = new ContextMetrics(
                retrievedChunks.size(),
                used.size(),
                discarded.size(),
                estimatedTokens,
                documents.size()
        );
        return new BuiltContext(contextText, metrics, List.copyOf(used), List.copyOf(discarded));
    }

    private String formatContext(List<RetrievedChunk> chunks) {
        var builder = new StringBuilder();
        for (int index = 0; index < chunks.size(); index++) {
            var chunk = chunks.get(index);
            builder.append("[Source ").append(index + 1).append("] ")
                    .append(chunk.documentTitle())
                    .append(" v").append(chunk.version())
                    .append(" chunk ").append(chunk.chunkIndex())
                    .append(System.lineSeparator())
                    .append(chunk.chunkText())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }
        return builder.toString().strip();
    }

    private int estimateTokens(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private String fingerprint(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180);
    }
}
