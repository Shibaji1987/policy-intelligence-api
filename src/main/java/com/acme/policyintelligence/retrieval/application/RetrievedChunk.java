package com.acme.policyintelligence.retrieval.application;

import java.util.UUID;

public record RetrievedChunk(
        UUID documentId,
        String documentTitle,
        UUID versionId,
        int version,
        UUID chunkId,
        int chunkIndex,
        String parentSectionId,
        String parentSectionTitle,
        String chunkText,
        double similarityScore,
        double keywordScore,
        double combinedScore,
        Integer vectorRank,
        Integer keywordRank,
        double rrfScore,
        String retrievalSource,
        Integer rerankRank,
        double rerankScore,
        String rerankReason,
        String matchedQueryVariant,
        String retrievalStrategy,
        String excerpt
) {
    public RetrievedChunk withFusion(Integer newVectorRank, Integer newKeywordRank, double newRrfScore, String newRetrievalSource) {
        return new RetrievedChunk(
                documentId,
                documentTitle,
                versionId,
                version,
                chunkId,
                chunkIndex,
                parentSectionId,
                parentSectionTitle,
                chunkText,
                similarityScore,
                keywordScore,
                combinedScore,
                newVectorRank,
                newKeywordRank,
                newRrfScore,
                newRetrievalSource,
                rerankRank,
                rerankScore,
                rerankReason,
                matchedQueryVariant,
                retrievalStrategy,
                excerpt
        );
    }

    public RetrievedChunk withRerank(int newRerankRank, double newRerankScore, String newRerankReason) {
        return new RetrievedChunk(
                documentId,
                documentTitle,
                versionId,
                version,
                chunkId,
                chunkIndex,
                parentSectionId,
                parentSectionTitle,
                chunkText,
                similarityScore,
                keywordScore,
                combinedScore,
                vectorRank,
                keywordRank,
                rrfScore,
                retrievalSource,
                newRerankRank,
                newRerankScore,
                newRerankReason,
                matchedQueryVariant,
                retrievalStrategy,
                excerpt
        );
    }

    public RetrievedChunk withMatchedQueryVariant(String queryVariant) {
        return new RetrievedChunk(
                documentId,
                documentTitle,
                versionId,
                version,
                chunkId,
                chunkIndex,
                parentSectionId,
                parentSectionTitle,
                chunkText,
                similarityScore,
                keywordScore,
                combinedScore,
                vectorRank,
                keywordRank,
                rrfScore,
                retrievalSource,
                rerankRank,
                rerankScore,
                rerankReason,
                queryVariant,
                retrievalStrategy,
                excerpt
        );
    }
}
