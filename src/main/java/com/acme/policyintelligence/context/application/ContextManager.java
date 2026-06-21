package com.acme.policyintelligence.context.application;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ContextManager {

    private final int maxChunks;
    private final int tokenBudget;
    private final int maxChunksPerDocument;

    public ContextManager(
            @Value("${app.advisor.max-context-chunks:8}") int maxChunks,
            @Value("${app.advisor.context-token-budget:1800}") int tokenBudget,
            @Value("${app.advisor.max-chunks-per-document:3}") int maxChunksPerDocument
    ) {
        this.maxChunks = maxChunks;
        this.tokenBudget = tokenBudget;
        this.maxChunksPerDocument = maxChunksPerDocument;
    }

    public BuiltContext build(List<RetrievedChunk> retrievedChunks) {
        var used = new ArrayList<RetrievedChunk>();
        var discarded = new ArrayList<RetrievedChunk>();
        var decisions = new ArrayList<ContextChunkDecision>();
        var fingerprints = new HashSet<String>();
        var tokenSets = new ArrayList<Set<String>>();
        var documents = new HashSet<String>();
        var documentCounts = new HashMap<String, Integer>();
        int estimatedTokens = 0;
        int duplicateDiscarded = 0;
        int nearDuplicateDiscarded = 0;
        int documentQuotaDiscarded = 0;
        int tokenBudgetDiscarded = 0;
        int maxChunkDiscarded = 0;

        for (RetrievedChunk chunk : retrievedChunks) {
            int chunkTokens = estimateTokens(chunk.chunkText());
            String fingerprint = fingerprint(chunk.chunkText());
            Set<String> tokenSet = tokenSet(chunk.chunkText());
            boolean duplicate = !fingerprints.add(fingerprint);
            boolean nearDuplicate = !duplicate && isNearDuplicate(tokenSet, tokenSets);
            boolean overBudget = estimatedTokens + chunkTokens > tokenBudget;
            boolean tooMany = used.size() >= maxChunks;
            String documentKey = chunk.documentId().toString();
            boolean documentQuotaExceeded = documentCounts.getOrDefault(documentKey, 0) >= maxChunksPerDocument;

            if (duplicate || nearDuplicate || overBudget || tooMany || documentQuotaExceeded) {
                discarded.add(chunk);
                String reason;
                if (duplicate) {
                    duplicateDiscarded++;
                    reason = "DUPLICATE";
                } else if (nearDuplicate) {
                    nearDuplicateDiscarded++;
                    reason = "NEAR_DUPLICATE";
                } else if (overBudget) {
                    tokenBudgetDiscarded++;
                    reason = "TOKEN_BUDGET";
                } else if (tooMany) {
                    maxChunkDiscarded++;
                    reason = "MAX_CONTEXT_CHUNKS";
                } else {
                    documentQuotaDiscarded++;
                    reason = "DOCUMENT_DIVERSITY_QUOTA";
                }
                decisions.add(new ContextChunkDecision(chunk, false, null, reason, chunkTokens));
                continue;
            }

            used.add(chunk);
            tokenSets.add(tokenSet);
            documents.add(documentKey);
            documentCounts.merge(documentKey, 1, Integer::sum);
            estimatedTokens += chunkTokens;
            decisions.add(new ContextChunkDecision(chunk, true, used.size(), "USED", chunkTokens));
        }

        String contextText = formatContext(used);
        var metrics = new ContextMetrics(
                retrievedChunks.size(),
                used.size(),
                discarded.size(),
                estimatedTokens,
                documents.size(),
                duplicateDiscarded,
                nearDuplicateDiscarded,
                documentQuotaDiscarded,
                tokenBudgetDiscarded,
                maxChunkDiscarded
        );
        return new BuiltContext(contextText, metrics, List.copyOf(used), List.copyOf(discarded), List.copyOf(decisions));
    }

    private String formatContext(List<RetrievedChunk> chunks) {
        var builder = new StringBuilder();
        for (int index = 0; index < chunks.size(); index++) {
            var chunk = chunks.get(index);
            builder.append("[Source ").append(index + 1).append("] ")
                    .append(chunk.documentTitle())
                    .append(" v").append(chunk.version())
                    .append(" ").append(chunk.parentSectionTitle())
                    .append(" chunk ").append(chunk.chunkIndex())
                    .append(" chunkId=").append(chunk.chunkId())
                    .append(" parentSectionId=").append(chunk.parentSectionId())
                    .append(" similarity=").append(String.format(Locale.ROOT, "%.4f", chunk.similarityScore()))
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

    private Set<String> tokenSet(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            return Set.of();
        }
        return Set.of(normalized.split("\\s+"));
    }

    private boolean isNearDuplicate(Set<String> current, List<Set<String>> existing) {
        if (current.isEmpty()) {
            return false;
        }
        for (Set<String> candidate : existing) {
            if (jaccard(current, candidate) >= 0.82) {
                return true;
            }
        }
        return false;
    }

    private double jaccard(Set<String> first, Set<String> second) {
        if (first.isEmpty() || second.isEmpty()) {
            return 0;
        }
        int intersection = 0;
        for (String token : first) {
            if (second.contains(token)) {
                intersection++;
            }
        }
        int union = first.size() + second.size() - intersection;
        return union == 0 ? 0 : (double) intersection / union;
    }
}
