package com.acme.policyintelligence.retrieval.infrastructure;

import com.acme.policyintelligence.embedding.infrastructure.ChunkEmbeddingRepository;
import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.retrieval.application.RetrievedChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class VectorSearchRepository {

    private static final int EXCERPT_LENGTH = 260;

    private final JdbcTemplate jdbcTemplate;
    private final ChunkEmbeddingRepository chunkEmbeddingRepository;

    public VectorSearchRepository(
            JdbcTemplate jdbcTemplate,
            ChunkEmbeddingRepository chunkEmbeddingRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.chunkEmbeddingRepository = chunkEmbeddingRepository;
    }

    public List<RetrievedChunk> search(float[] queryEmbedding, String query, int topK, RetrievalFilters filters) {
        return vectorSearch(queryEmbedding, query, topK, filters);
    }

    public List<RetrievedChunk> vectorSearch(float[] queryEmbedding, String query, int topK, RetrievalFilters filters) {
        String vectorLiteral = chunkEmbeddingRepository.toVectorLiteral(queryEmbedding);
        return jdbcTemplate.query(
                """
                        WITH scored_chunks AS (
                            SELECT
                                chunk.document_id,
                                document.title AS document_title,
                                chunk.version_id,
                                version.version_number,
                                chunk.id AS chunk_id,
                                chunk.chunk_index,
                                ('section-' || FLOOR(chunk.chunk_index / 5.0)::int) AS parent_section_id,
                                ('Section ' || (FLOOR(chunk.chunk_index / 5.0)::int + 1)) AS parent_section_title,
                                chunk.chunk_text,
                                1 - (chunk.embedding <=> ?::vector) AS similarity_score,
                                ts_rank_cd(to_tsvector('english', chunk.chunk_text), plainto_tsquery('english', ?)) AS keyword_score
                            FROM document_chunk chunk
                            JOIN document document ON document.id = chunk.document_id
                            JOIN document_version version ON version.id = chunk.version_id
                            WHERE chunk.active = true
                              AND chunk.embedding_status = 'COMPLETED'
                              AND chunk.embedding IS NOT NULL
                              AND document.tenant_id = ?
                              AND (? IS NULL OR document.department = ?)
                              AND (? IS NULL OR document.region = ?)
                              AND (? IS NULL OR document.document_type = ?)
                              AND (? IS NULL OR document.classification = ?)
                        )
                        SELECT
                            *,
                            (0.78 * similarity_score) + (0.22 * LEAST(keyword_score, 1.0)) AS combined_score
                        FROM scored_chunks
                        ORDER BY combined_score DESC, similarity_score DESC
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> {
                    String chunkText = resultSet.getString("chunk_text");
                    return new RetrievedChunk(
                            resultSet.getObject("document_id", java.util.UUID.class),
                            resultSet.getString("document_title"),
                            resultSet.getObject("version_id", java.util.UUID.class),
                            resultSet.getInt("version_number"),
                            resultSet.getObject("chunk_id", java.util.UUID.class),
                            resultSet.getInt("chunk_index"),
                            resultSet.getString("parent_section_id"),
                            resultSet.getString("parent_section_title"),
                            chunkText,
                            resultSet.getDouble("similarity_score"),
                            resultSet.getDouble("keyword_score"),
                            resultSet.getDouble("combined_score"),
                            rowNumber + 1,
                            resultSet.getDouble("keyword_score") > 0 ? rowNumber + 1 : null,
                            0,
                            resultSet.getDouble("keyword_score") > 0 ? "BOTH" : "VECTOR",
                            null,
                            0,
                            null,
                            "HYBRID",
                            excerpt(chunkText)
                    );
                },
                vectorLiteral,
                sanitizeQuery(query),
                filters.tenantId(),
                filters.department(),
                filters.department(),
                filters.region(),
                filters.region(),
                filters.documentType(),
                filters.documentType(),
                filters.classification(),
                filters.classification(),
                topK
        );
    }

    public List<RetrievedChunk> keywordSearch(String query, int topK, RetrievalFilters filters) {
        return jdbcTemplate.query(
                """
                        SELECT
                            chunk.document_id,
                            document.title AS document_title,
                            chunk.version_id,
                            version.version_number,
                            chunk.id AS chunk_id,
                            chunk.chunk_index,
                            ('section-' || FLOOR(chunk.chunk_index / 5.0)::int) AS parent_section_id,
                            ('Section ' || (FLOOR(chunk.chunk_index / 5.0)::int + 1)) AS parent_section_title,
                            chunk.chunk_text,
                            0.0 AS similarity_score,
                            ts_rank_cd(to_tsvector('english', chunk.chunk_text), plainto_tsquery('english', ?)) AS keyword_score
                        FROM document_chunk chunk
                        JOIN document document ON document.id = chunk.document_id
                        JOIN document_version version ON version.id = chunk.version_id
                        WHERE chunk.active = true
                          AND chunk.embedding_status = 'COMPLETED'
                          AND document.tenant_id = ?
                          AND (? IS NULL OR document.department = ?)
                          AND (? IS NULL OR document.region = ?)
                          AND (? IS NULL OR document.document_type = ?)
                          AND (? IS NULL OR document.classification = ?)
                          AND to_tsvector('english', chunk.chunk_text) @@ plainto_tsquery('english', ?)
                        ORDER BY keyword_score DESC
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> {
                    String chunkText = resultSet.getString("chunk_text");
                    double keywordScore = resultSet.getDouble("keyword_score");
                    return new RetrievedChunk(
                            resultSet.getObject("document_id", java.util.UUID.class),
                            resultSet.getString("document_title"),
                            resultSet.getObject("version_id", java.util.UUID.class),
                            resultSet.getInt("version_number"),
                            resultSet.getObject("chunk_id", java.util.UUID.class),
                            resultSet.getInt("chunk_index"),
                            resultSet.getString("parent_section_id"),
                            resultSet.getString("parent_section_title"),
                            chunkText,
                            0,
                            keywordScore,
                            keywordScore,
                            null,
                            rowNumber + 1,
                            0,
                            "KEYWORD",
                            null,
                            0,
                            null,
                            "KEYWORD",
                            excerpt(chunkText)
                    );
                },
                sanitizeQuery(query),
                filters.tenantId(),
                filters.department(),
                filters.department(),
                filters.region(),
                filters.region(),
                filters.documentType(),
                filters.documentType(),
                filters.classification(),
                filters.classification(),
                sanitizeQuery(query),
                topK
        );
    }

    public List<RetrievedChunk> findActiveNeighbors(List<RetrievedChunk> seeds) {
        if (seeds.isEmpty()) {
            return List.of();
        }
        return seeds.stream()
                .flatMap(seed -> findActiveNeighbors(seed).stream())
                .toList();
    }

    private List<RetrievedChunk> findActiveNeighbors(RetrievedChunk seed) {
        return jdbcTemplate.query(
                """
                        SELECT
                            chunk.document_id,
                            document.title AS document_title,
                            chunk.version_id,
                            version.version_number,
                            chunk.id AS chunk_id,
                            chunk.chunk_index,
                            ('section-' || FLOOR(chunk.chunk_index / 5.0)::int) AS parent_section_id,
                            ('Section ' || (FLOOR(chunk.chunk_index / 5.0)::int + 1)) AS parent_section_title,
                            chunk.chunk_text,
                            0.0 AS similarity_score,
                            0.0 AS keyword_score,
                            0.0 AS combined_score
                        FROM document_chunk chunk
                        JOIN document document ON document.id = chunk.document_id
                        JOIN document_version version ON version.id = chunk.version_id
                        WHERE chunk.active = true
                          AND chunk.embedding_status = 'COMPLETED'
                          AND chunk.version_id = ?
                          AND chunk.chunk_index IN (?, ?)
                        ORDER BY chunk.chunk_index
                        """,
                (resultSet, rowNumber) -> {
                    String chunkText = resultSet.getString("chunk_text");
                    return new RetrievedChunk(
                            resultSet.getObject("document_id", java.util.UUID.class),
                            resultSet.getString("document_title"),
                            resultSet.getObject("version_id", java.util.UUID.class),
                            resultSet.getInt("version_number"),
                            resultSet.getObject("chunk_id", java.util.UUID.class),
                            resultSet.getInt("chunk_index"),
                            resultSet.getString("parent_section_id"),
                            resultSet.getString("parent_section_title"),
                            chunkText,
                            seed.similarityScore() * 0.96,
                            0,
                            seed.combinedScore() * 0.92,
                            seed.vectorRank(),
                            null,
                            seed.rrfScore() * 0.92,
                            "PARENT_CHILD",
                            seed.rerankRank(),
                            seed.rerankScore() * 0.92,
                            "Neighbor chunk expanded from high-ranking parent section",
                            "PARENT_CHILD_NEIGHBOR",
                            excerpt(chunkText)
                    );
                },
                seed.versionId(),
                seed.chunkIndex() - 1,
                seed.chunkIndex() + 1
        );
    }

    private String sanitizeQuery(String query) {
        return query == null || query.isBlank() ? "policy" : query.strip();
    }

    private String excerpt(String text) {
        if (text.length() <= EXCERPT_LENGTH) {
            return text;
        }
        return text.substring(0, EXCERPT_LENGTH).stripTrailing() + "...";
    }
}
