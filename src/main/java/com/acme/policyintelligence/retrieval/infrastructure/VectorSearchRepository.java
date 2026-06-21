package com.acme.policyintelligence.retrieval.infrastructure;

import com.acme.policyintelligence.embedding.infrastructure.ChunkEmbeddingRepository;
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

    public List<RetrievedChunk> search(float[] queryEmbedding, int topK) {
        String vectorLiteral = chunkEmbeddingRepository.toVectorLiteral(queryEmbedding);
        return jdbcTemplate.query(
                """
                        SELECT
                            chunk.document_id,
                            document.title AS document_title,
                            chunk.version_id,
                            version.version_number,
                            chunk.id AS chunk_id,
                            chunk.chunk_index,
                            chunk.chunk_text,
                            1 - (chunk.embedding <=> ?::vector) AS similarity_score
                        FROM document_chunk chunk
                        JOIN document document ON document.id = chunk.document_id
                        JOIN document_version version ON version.id = chunk.version_id
                        WHERE chunk.active = true
                          AND chunk.embedding_status = 'COMPLETED'
                          AND chunk.embedding IS NOT NULL
                        ORDER BY chunk.embedding <=> ?::vector
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
                            chunkText,
                            resultSet.getDouble("similarity_score"),
                            excerpt(chunkText)
                    );
                },
                vectorLiteral,
                vectorLiteral,
                topK
        );
    }

    private String excerpt(String text) {
        if (text.length() <= EXCERPT_LENGTH) {
            return text;
        }
        return text.substring(0, EXCERPT_LENGTH).stripTrailing() + "...";
    }
}
