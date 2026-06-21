package com.acme.policyintelligence.trace.infrastructure;

import com.acme.policyintelligence.context.application.ContextMetrics;
import com.acme.policyintelligence.context.application.ContextChunkDecision;
import com.acme.policyintelligence.ml.application.RetrievalQualityPrediction;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import com.acme.policyintelligence.trace.application.RetrievalTraceDetail;
import com.acme.policyintelligence.trace.application.RetrievalTraceSourceSummary;
import com.acme.policyintelligence.trace.application.RetrievalTraceSummary;
import com.acme.policyintelligence.trace.application.RetrievalTraceTimings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            List<ContextChunkDecision> decisions,
            ContextMetrics metrics,
            RetrievalQualityPrediction prediction,
            long corpusVersion,
            boolean cacheHit,
            RetrievalTraceTimings timings,
            String answerGenerator,
            String retrievalStrategy,
            String queryPlan,
            boolean answerVerified,
            String answerVerificationReason
    ) {
        UUID traceId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO retrieval_trace (
                            id, question, refined_query, answer, retrieved_chunks, used_chunks,
                            discarded_chunks, estimated_tokens, top_similarity_score,
                            avg_top5_similarity, document_diversity, ml_label, ml_probability,
                            corpus_version, cache_hit, retrieval_latency_ms, context_build_latency_ms,
                            llm_latency_ms, ml_latency_ms, total_latency_ms, answer_generator,
                            retrieval_strategy, query_plan, answer_verified, answer_verification_reason, created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                corpusVersion,
                cacheHit,
                timings.retrievalLatencyMs(),
                timings.contextBuildLatencyMs(),
                timings.llmLatencyMs(),
                timings.mlLatencyMs(),
                timings.totalLatencyMs(),
                answerGenerator,
                retrievalStrategy,
                queryPlan,
                answerVerified,
                answerVerificationReason,
                Timestamp.from(Instant.now())
        );

        Map<UUID, ContextChunkDecision> decisionByChunk = new HashMap<>();
        for (ContextChunkDecision decision : decisions) {
            decisionByChunk.put(decision.chunk().chunkId(), decision);
        }

        for (int index = 0; index < retrieved.size(); index++) {
            RetrievedChunk chunk = retrieved.get(index);
            ContextChunkDecision decision = decisionByChunk.get(chunk.chunkId());
            jdbcTemplate.update(
                    """
                            INSERT INTO retrieval_trace_source (
                                id, trace_id, document_id, document_title, version_id, version_number,
                                chunk_id, chunk_index, similarity_score, excerpt, used_in_context,
                                source_rank, context_rank, discard_reason, token_estimate,
                                keyword_score, combined_score, retrieval_strategy
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    used.stream().anyMatch(source -> source.chunkId().equals(chunk.chunkId())),
                    index + 1,
                    decision == null ? null : decision.contextRank(),
                    decision == null ? "NOT_EVALUATED" : decision.reason(),
                    decision == null ? 0 : decision.tokenEstimate(),
                    chunk.keywordScore(),
                    chunk.combinedScore(),
                    chunk.retrievalStrategy()
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
                (rs, row) -> mapTraceSummary(rs),
                limit
        );
    }

    public Optional<RetrievalTraceDetail> findDetail(UUID traceId) {
        List<RetrievalTraceSummary> summaries = jdbcTemplate.query(
                """
                        SELECT *
                        FROM retrieval_trace
                        WHERE id = ?
                        """,
                (rs, row) -> mapTraceSummary(rs),
                traceId
        );
        if (summaries.isEmpty()) {
            return Optional.empty();
        }

        List<RetrievalTraceSourceSummary> sources = jdbcTemplate.query(
                """
                        SELECT *
                        FROM retrieval_trace_source
                        WHERE trace_id = ?
                        ORDER BY source_rank, chunk_index
                        """,
                (rs, row) -> new RetrievalTraceSourceSummary(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("document_title"),
                        rs.getObject("version_id", UUID.class),
                        rs.getInt("version_number"),
                        rs.getObject("chunk_id", UUID.class),
                        rs.getInt("chunk_index"),
                        rs.getDouble("similarity_score"),
                        rs.getString("excerpt"),
                        rs.getBoolean("used_in_context"),
                        rs.getInt("source_rank"),
                        (Integer) rs.getObject("context_rank"),
                        rs.getString("discard_reason"),
                        rs.getInt("token_estimate"),
                        rs.getDouble("keyword_score"),
                        rs.getDouble("combined_score"),
                        rs.getString("retrieval_strategy")
                ),
                traceId
        );
        return Optional.of(new RetrievalTraceDetail(summaries.getFirst(), sources));
    }

    public UUID saveFeedback(UUID traceId, String rating, String comment) {
        if (findDetail(traceId).isEmpty()) {
            throw new IllegalArgumentException("Retrieval trace not found: " + traceId);
        }
        UUID feedbackId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO retrieval_feedback (id, trace_id, rating, comment, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                feedbackId,
                traceId,
                normalizeRating(rating),
                comment,
                Timestamp.from(Instant.now())
        );
        return feedbackId;
    }

    private String normalizeRating(String rating) {
        if (rating == null || rating.isBlank()) {
            throw new IllegalArgumentException("Feedback rating is required");
        }
        String normalized = rating.strip().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("THUMBS_UP", "THUMBS_DOWN", "GOOD_RETRIEVAL", "BAD_RETRIEVAL").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported feedback rating: " + rating);
        }
        return normalized;
    }

    private RetrievalTraceSummary mapTraceSummary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RetrievalTraceSummary(
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
                rs.getLong("corpus_version"),
                rs.getBoolean("cache_hit"),
                rs.getLong("retrieval_latency_ms"),
                rs.getLong("context_build_latency_ms"),
                rs.getLong("llm_latency_ms"),
                rs.getLong("ml_latency_ms"),
                rs.getLong("total_latency_ms"),
                rs.getString("answer_generator"),
                rs.getString("retrieval_strategy"),
                rs.getString("query_plan"),
                rs.getBoolean("answer_verified"),
                rs.getString("answer_verification_reason"),
                rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC)
        );
    }

    private Double topSimilarity(List<RetrievedChunk> chunks) {
        return chunks.isEmpty() ? null : chunks.getFirst().similarityScore();
    }

    private Double avgTop5Similarity(List<RetrievedChunk> chunks) {
        return chunks.stream().limit(5).mapToDouble(RetrievedChunk::similarityScore).average().orElse(0);
    }
}
