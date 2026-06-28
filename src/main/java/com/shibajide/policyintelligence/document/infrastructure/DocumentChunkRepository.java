package com.shibajide.policyintelligence.document.infrastructure;

import com.shibajide.policyintelligence.document.domain.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByVersionIdOrderByChunkIndex(UUID versionId);

    @Modifying(clearAutomatically = true)
    @Query("update DocumentChunk chunk set chunk.active = false where chunk.documentId = :documentId and chunk.active = true")
    int deactivateActiveChunks(UUID documentId);
}
