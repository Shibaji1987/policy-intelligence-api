package com.shibajide.policyintelligence.document.application;

import com.shibajide.policyintelligence.document.infrastructure.DocumentChunkRepository;
import com.shibajide.policyintelligence.document.infrastructure.DocumentRepository;
import com.shibajide.policyintelligence.document.infrastructure.DocumentVersionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DocumentQueryService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentChunkRepository chunkRepository;

    public DocumentQueryService(
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            DocumentChunkRepository chunkRepository
    ) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.chunkRepository = chunkRepository;
    }

    public Page<DocumentSummary> findDocuments(Pageable pageable) {
        return documentRepository.findAll(capped(pageable))
                .map(document -> new DocumentSummary(
                        document.getId(),
                        document.getTitle(),
                        document.getTenantId(),
                        document.getDepartment(),
                        document.getRegion(),
                        document.getDocumentType(),
                        document.getClassification(),
                        document.getCreatedAt(),
                        document.getUpdatedAt()
                ));
    }

    public List<DocumentVersionSummary> findVersions(UUID documentId) {
        requireDocument(documentId);
        return versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId).stream()
                .map(version -> new DocumentVersionSummary(
                        version.getId(),
                        version.getVersionNumber(),
                        version.getOriginalFilename(),
                        version.getMediaType(),
                        version.getChunkingStrategy(),
                        version.getChunkSize(),
                        version.getChunkOverlap(),
                        version.getCreatedAt()
                ))
                .toList();
    }

    public List<DocumentChunkSummary> findChunks(UUID versionId) {
        if (!versionRepository.existsById(versionId)) {
            throw new IllegalArgumentException("Document version not found: " + versionId);
        }
        return chunkRepository.findByVersionIdOrderByChunkIndex(versionId).stream()
                .map(chunk -> new DocumentChunkSummary(
                        chunk.getId(),
                        chunk.getDocumentId(),
                        chunk.getVersionId(),
                        chunk.getChunkIndex(),
                        chunk.getChunkText(),
                        chunk.getMetadata(),
                        chunk.getEmbeddingStatus(),
                        chunk.getEmbeddingModel(),
                        chunk.getEmbeddingDimension(),
                        chunk.getEmbeddedAt(),
                        chunk.getEmbeddingFailureReason(),
                        chunk.getEmbeddingAttempts(),
                        chunk.getLastEmbeddingAttemptAt(),
                        chunk.isActive()
                ))
                .toList();
    }

    private void requireDocument(UUID documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new DocumentNotFoundException(documentId);
        }
    }

    private Pageable capped(Pageable pageable) {
        int page = pageable == null ? 0 : Math.max(0, pageable.getPageNumber());
        int size = pageable == null ? 25 : Math.clamp(pageable.getPageSize(), 1, 100);
        return PageRequest.of(page, size, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC,
                "updatedAt"
        ));
    }
}
