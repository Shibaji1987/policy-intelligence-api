package com.acme.policyintelligence.trace.infrastructure;

import com.acme.policyintelligence.context.application.ContextMetrics;
import com.acme.policyintelligence.ml.application.RetrievalQualityPrediction;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.trace.application.RetrievalTraceSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class RetrievalTraceRepository {

    private final JdbcTemplate jdbcTemplate;

    public RetrievalTraceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID save(
            String question,
            String refinedQuery,
            String answer,
            List<RetrievedChunk> retrieved,
            List<RetrievedChunk> used,
            ContextMetrics metrics,
            RetrievalQualityPrediction prediction
    ) {
        UUID traceId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO retrieval_trace (
                            id, question, refined_query, answer, retrieved_chunks, used_chunks,
                            discarded_chunks, estimated_tokens, top_similarity_score,
                            avg_top5_similarity, document_diversity, ml_label, ml_probability, created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                traceId,
                question,
                refinedQuery,
                answer,
                metrics.retrievedChunks(),
                metrics.usedChunks(),
                metrics.discardedChunks(),
                metrics.estimatedTokens(),
                topSimilarity(retrieved),
                avgTop5Similarity(retrieved),
                metrics.documentDiversity(),
                prediction.label(),
                prediction.probability(),
                Timestamp.from(Instant.now())
        );

        for (RetrievedChunk chunk : retrieved) {
            jdbcTemplate.update(
                    """
                            INSERT INTO retrieval_trace_source (
                                id, trace_id, document_id, document_title, version_id, version_number,
                                chunk_id, chunk_index, similarity_score, excerpt, used_in_context
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    UUID.randomUUID(),
                    traceId,
                    chunk.documentId(),
                    chunk.documentTitle(),
                    chunk.versionId(),
                    chunk.version(),
                    chunk.chunkId(),
                    chunk.chunkIndex(),
                    chunk.similarityScore(),
                    chunk.excerpt(),
                    used.stream().anyMatch(source -> source.chunkId().equals(chunk.chunkId()))
            );
        }
        return traceId;
    }

    public List<RetrievalTraceSummary> findRecent(int limit) {
        return jdbcTemplate.query(
                """
                        SELECT *
                        FROM retrieval_trace
                        ORDER BY created_at DESC
                        LIMIT ?
                        """,
                (rs, row) -> new RetrievalTraceSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("question"),
                        rs.getString("refined_query"),
                        rs.getString("answer"),
                        rs.getInt("retrieved_chunks"),
                        rs.getInt("used_chunks"),
                        rs.getInt("discarded_chunks"),
                        rs.getInt("estimated_tokens"),
                        (Double) rs.getObject("top_similarity_score"),
                        (Double) rs.getObject("avg_top5_similarity"),
                        rs.getInt("document_diversity"),
                        rs.getString("ml_label"),
                        (Double) rs.getObject("ml_probability"),
                        rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC)
                ),
                limit
        );
    }

    private Double topSimilarity(List<RetrievedChunk> chunks) {
        return chunks.isEmpty() ? null : chunks.getFirst().similarityScore();
    }

    private Double avgTop5Similarity(List<RetrievedChunk> chunks) {
        return chunks.stream().limit(5).mapToDouble(RetrievedChunk::similarityScore).average().orElse(0);
    }
}
