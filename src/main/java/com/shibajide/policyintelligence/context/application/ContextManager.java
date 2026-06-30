package com.shibajide.policyintelligence.context.application;

import com.shibajide.policyintelligence.context.compression.CompressedChunk;
import com.shibajide.policyintelligence.context.compression.ContextCompressionService;
import com.shibajide.policyintelligence.context.packing.ContextPackingService;
import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;
import com.shibajide.policyintelligence.shared.text.TokenEstimator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ContextManager {

    private final int maxChunks;
    private final int tokenBudget;
    private final int maxChunksPerDocument;
    private final ContextCompressionService compressionService;
    private final ContextPackingService packingService;
    private final TokenEstimator tokenEstimator;

    public ContextManager(
            @Value("${app.advisor.max-context-chunks:8}") int maxChunks,
            @Value("${app.advisor.context-token-budget:1800}") int tokenBudget,
            @Value("${app.advisor.max-chunks-per-document:3}") int maxChunksPerDocument,
            ContextCompressionService compressionService,
            ContextPackingService packingService,
            TokenEstimator tokenEstimator
    ) {
        this.maxChunks = maxChunks;
        this.tokenBudget = tokenBudget;
        this.maxChunksPerDocument = maxChunksPerDocument;
        this.compressionService = compressionService;
        this.packingService = packingService;
        this.tokenEstimator = tokenEstimator;
    }

    public BuiltContext build(List<RetrievedChunk> retrievedChunks) {
        return build("", retrievedChunks);
    }

    public BuiltContext build(String question, List<RetrievedChunk> retrievedChunks) {
        var compressedContext = compressionService.compress(question, retrievedChunks);
        ContextSelection selection = selectChunks(retrievedChunks, compressedByChunk(compressedContext.chunks()));
        String contextText = packedContextText(selection);
        return new BuiltContext(
                contextText,
                selection.metrics(retrievedChunks.size()),
                List.copyOf(selection.used),
                List.copyOf(selection.discarded),
                List.copyOf(selection.decisions)
        );
    }

    private Map<java.util.UUID, CompressedChunk> compressedByChunk(List<CompressedChunk> chunks) {
        return chunks.stream()
                .collect(Collectors.toMap(chunk -> chunk.source().chunkId(), chunk -> chunk));
    }

    private ContextSelection selectChunks(
            List<RetrievedChunk> retrievedChunks,
            Map<java.util.UUID, CompressedChunk> compressedByChunk
    ) {
        ContextSelection selection = new ContextSelection(compressedByChunk);
        retrievedChunks.stream()
                .map(selection::candidate)
                .forEach(selection::apply);
        return selection;
    }

    private String packedContextText(ContextSelection selection) {
        var packed = packingService.pack(selection.used.stream()
                .map(chunk -> selection.compressedByChunk.get(chunk.chunkId()))
                .filter(Objects::nonNull)
                .toList());
        return formatContext(packed.orderedChunks());
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
        return new LinkedHashSet<>(List.of(normalized.split("\\s+")));
    }

    private boolean isNearDuplicate(Set<String> current, List<Set<String>> existing) {
        if (current.isEmpty()) {
            return false;
        }
        return existing.stream().anyMatch(candidate -> jaccard(current, candidate) >= 0.82);
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

    private enum DiscardReason {
        DUPLICATE,
        NEAR_DUPLICATE,
        TOKEN_BUDGET_EXCEEDED,
        LOW_RANK,
        DOCUMENT_DIVERSITY_LIMIT
    }

    private record ContextCandidate(
            RetrievedChunk chunk,
            CompressedChunk compressed,
            int originalTokens,
            int chunkTokens,
            String fingerprint,
            Set<String> tokenSet
    ) {
        String documentKey() {
            return chunk.documentId().toString();
        }

        double compressionRatio() {
            return compressed == null ? 1 : compressed.compressionRatio();
        }

        String compressionMethod() {
            return compressed == null ? "NONE" : compressed.compressionMethod();
        }

        String originalText() {
            return compressed == null ? chunk.chunkText() : compressed.originalText();
        }

        String compressedText() {
            return compressed == null ? chunk.chunkText() : compressed.compressedText();
        }
    }

    private class ContextSelection {
        private final Map<java.util.UUID, CompressedChunk> compressedByChunk;
        private final List<RetrievedChunk> used = new ArrayList<>();
        private final List<RetrievedChunk> discarded = new ArrayList<>();
        private final List<ContextChunkDecision> decisions = new ArrayList<>();
        private final Set<String> fingerprints = new HashSet<>();
        private final List<Set<String>> tokenSets = new ArrayList<>();
        private final Set<String> documents = new HashSet<>();
        private final Map<String, Integer> documentCounts = new HashMap<>();
        private int estimatedTokens;
        private int duplicateDiscarded;
        private int nearDuplicateDiscarded;
        private int documentQuotaDiscarded;
        private int tokenBudgetDiscarded;
        private int maxChunkDiscarded;

        ContextSelection(Map<java.util.UUID, CompressedChunk> compressedByChunk) {
            this.compressedByChunk = compressedByChunk;
        }

        ContextCandidate candidate(RetrievedChunk chunk) {
            CompressedChunk compressed = compressedByChunk.get(chunk.chunkId());
            int originalTokens = compressed == null ? tokenEstimator.estimate(chunk.chunkText()) : compressed.originalTokenCount();
            int chunkTokens = compressed == null ? tokenEstimator.estimate(chunk.chunkText()) : compressed.compressedTokenCount();
            return new ContextCandidate(
                    chunk,
                    compressed,
                    originalTokens,
                    chunkTokens,
                    fingerprint(chunk.chunkText()),
                    tokenSet(chunk.chunkText())
            );
        }

        void apply(ContextCandidate candidate) {
            DiscardReason reason = discardReason(candidate);
            if (reason == null) {
                use(candidate);
            } else {
                discard(candidate, reason);
            }
        }

        private DiscardReason discardReason(ContextCandidate candidate) {
            if (!fingerprints.add(candidate.fingerprint())) {
                return DiscardReason.DUPLICATE;
            }
            if (isNearDuplicate(candidate.tokenSet(), tokenSets)) {
                return DiscardReason.NEAR_DUPLICATE;
            }
            if (estimatedTokens + candidate.chunkTokens() > tokenBudget) {
                return DiscardReason.TOKEN_BUDGET_EXCEEDED;
            }
            if (used.size() >= maxChunks) {
                return DiscardReason.LOW_RANK;
            }
            if (documentCounts.getOrDefault(candidate.documentKey(), 0) >= maxChunksPerDocument) {
                return DiscardReason.DOCUMENT_DIVERSITY_LIMIT;
            }
            return null;
        }

        private void use(ContextCandidate candidate) {
            used.add(candidate.chunk());
            tokenSets.add(candidate.tokenSet());
            documents.add(candidate.documentKey());
            documentCounts.merge(candidate.documentKey(), 1, Integer::sum);
            estimatedTokens += candidate.chunkTokens();
            decisions.add(decision(candidate, true, used.size(), "USED"));
        }

        private void discard(ContextCandidate candidate, DiscardReason reason) {
            discarded.add(candidate.chunk());
            increment(reason);
            decisions.add(decision(candidate, false, null, reason.name()));
        }

        private ContextChunkDecision decision(
                ContextCandidate candidate,
                boolean used,
                Integer rank,
                String reason
        ) {
            return new ContextChunkDecision(
                    candidate.chunk(),
                    used,
                    rank,
                    reason,
                    candidate.chunkTokens(),
                    candidate.originalTokens(),
                    candidate.chunkTokens(),
                    candidate.compressionRatio(),
                    candidate.compressionMethod(),
                    candidate.originalText(),
                    candidate.compressedText()
            );
        }

        private void increment(DiscardReason reason) {
            switch (reason) {
                case DUPLICATE -> duplicateDiscarded++;
                case NEAR_DUPLICATE -> nearDuplicateDiscarded++;
                case TOKEN_BUDGET_EXCEEDED -> tokenBudgetDiscarded++;
                case LOW_RANK -> maxChunkDiscarded++;
                case DOCUMENT_DIVERSITY_LIMIT -> documentQuotaDiscarded++;
                default -> throw new IllegalStateException("Unsupported discard reason: " + reason);
            }
        }

        private ContextMetrics metrics(int retrievedCount) {
            return new ContextMetrics(
                    retrievedCount,
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
        }
    }
}
