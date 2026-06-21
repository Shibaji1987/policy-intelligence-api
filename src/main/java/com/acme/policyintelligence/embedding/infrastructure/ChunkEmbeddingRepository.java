package com.acme.policyintelligence.embedding.infrastructure;

import com.acme.policyintelligence.document.domain.EmbeddingStatus;
import com.acme.policyintelligence.embedding.application.EmbeddingVector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class ChunkEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChunkEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ChunkToEmbed> findPendingChunks(int limit) {
        return jdbcTemplate.query(
                """
                        SELECT id, chunk_text
                        FROM document_chunk
                        WHERE active = true AND embedding_status = 'PENDING'
                        ORDER BY created_at, chunk_index
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> new ChunkToEmbed(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("chunk_text")
                ),
                limit
        );
    }

    public int countPendingChunks() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM document_chunk WHERE active = true AND embedding_status = 'PENDING'",
                Integer.class
        );
    }

    public void markCompleted(UUID chunkId, EmbeddingVector embedding) {
        jdbcTemplate.update(
                """
                        UPDATE document_chunk
                        SET embedding = ?::vector,
                            embedding_status = ?,
                            embedding_model = ?,
                            embedding_dimension = ?,
                            embedded_at = ?
                        WHERE id = ?
                        """,
                toVectorLiteral(embedding.values()),
                EmbeddingStatus.COMPLETED.name(),
                embedding.model(),
                embedding.dimension(),
                Timestamp.from(Instant.now()),
                chunkId
        );
    }

    public void markFailed(UUID chunkId) {
        jdbcTemplate.update(
                "UPDATE document_chunk SET embedding_status = ? WHERE id = ?",
                EmbeddingStatus.FAILED.name(),
                chunkId
        );
    }

    public String toVectorLiteral(float[] vector) {
        var builder = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(vector[index]));
        }
        return builder.append(']').toString();
    }

    public record ChunkToEmbed(UUID id, String text) {
    }
}
