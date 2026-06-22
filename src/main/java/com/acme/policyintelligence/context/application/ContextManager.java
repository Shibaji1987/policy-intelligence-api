package com.acme.policyintelligence.context.application;

import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.context.compression.CompressedChunk;
import com.acme.policyintelligence.context.compression.ContextCompressionService;
import com.acme.policyintelligence.context.packing.ContextPackingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ContextManager {

    private final int maxChunks;
    private final int tokenBudget;
    private final int maxChunksPerDocument;
    private final ContextCompressionService compressionService;
    private final ContextPackingService packingService;

    public ContextManager(
            @Value("${app.advisor.max-context-chunks:8}") int maxChunks,
            @Value("${app.advisor.context-token-budget:1800}") int tokenBudget,
            @Value("${app.advisor.max-chunks-per-document:3}") int maxChunksPerDocument,
            ContextCompressionService compressionService,
            ContextPackingService packingService
    ) {
        this.maxChunks = maxChunks;
        this.tokenBudget = tokenBudget;
        this.maxChunksPerDocument = maxChunksPerDocument;
        this.compressionService = compressionService;
        this.packingService = packingService;
    }

    public BuiltContext build(List<RetrievedChunk> retrievedChunks) {
        return build("", retrievedChunks);
    }

    public BuiltContext build(String question, List<RetrievedChunk> retrievedChunks) {
        var compressedContext = compressionService.compress(question, retrievedChunks);
        Map<java.util.UUID, CompressedChunk> compressedByChunk = compressedContext.chunks().stream()
                .collect(Collectors.toMap(chunk -> chunk.source().chunkId(), chunk -> chunk));
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
            CompressedChunk compressed = compressedByChunk.get(chunk.chunkId());
            int originalTokens = compressed == null ? estimateTokens(chunk.chunkText()) : compressed.originalTokenCount();
            int chunkTokens = compressed == null ? estimateTokens(chunk.chunkText()) : compressed.compressedTokenCount();
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
                decisions.add(new ContextChunkDecision(
                        chunk,
                        false,
                        null,
                        reason,
                        chunkTokens,
                        originalTokens,
                        chunkTokens,
                        compressed == null ? 1 : compressed.compressionRatio(),
                        compressed == null ? "NONE" : compressed.compressionMethod()
                ));
                continue;
            }

            used.add(chunk);
            tokenSets.add(tokenSet);
            documents.add(documentKey);
            documentCounts.merge(documentKey, 1, Integer::sum);
            estimatedTokens += chunkTokens;
            decisions.add(new ContextChunkDecision(
                    chunk,
                    true,
                    used.size(),
                    "USED",
                    chunkTokens,
                    originalTokens,
                    chunkTokens,
                    compressed == null ? 1 : compressed.compressionRatio(),
                    compressed == null ? "NONE" : compressed.compressionMethod()
            ));
        }

        var packed = packingService.pack(used.stream()
                .map(chunk -> compressedByChunk.get(chunk.chunkId()))
                .filter(java.util.Objects::nonNull)
                .toList());
        String contextText = formatContext(packed.orderedChunks());
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

    private String formatContext(List<CompressedChunk> chunks) {
        var builder = new StringBuilder();
        for (int index = 0; index < chunks.size(); index++) {
            var compressed = chunks.get(index);
            var chunk = compressed.source();
            builder.append("[Source ").append(index + 1).append("] ")
                    .append(chunk.documentTitle())
                    .append(" v").append(chunk.version())
                    .append(" ").append(chunk.parentSectionTitle())
                    .append(" chunk ").append(chunk.chunkIndex())
                    .append(" chunkId=").append(chunk.chunkId())
                    .append(" parentSectionId=").append(chunk.parentSectionId())
                    .append(" similarity=").append(String.format(Locale.ROOT, "%.4f", chunk.similarityScore()))
                    .append(" compressedRatio=").append(String.format(Locale.ROOT, "%.2f", compressed.compressionRatio()))
                    .append(System.lineSeparator())
                    .append(compressed.compressedText())
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
