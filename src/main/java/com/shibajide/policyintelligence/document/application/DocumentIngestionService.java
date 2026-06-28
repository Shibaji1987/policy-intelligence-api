package com.shibajide.policyintelligence.document.application;

import com.shibajide.policyintelligence.chunking.ChunkerRegistry;
import com.shibajide.policyintelligence.document.domain.Document;
import com.shibajide.policyintelligence.document.domain.DocumentChunk;
import com.shibajide.policyintelligence.document.domain.DocumentVersion;
import com.shibajide.policyintelligence.document.infrastructure.CorpusStateRepository;
import com.shibajide.policyintelligence.document.infrastructure.DocumentChunkRepository;
import com.shibajide.policyintelligence.document.infrastructure.DocumentRepository;
import com.shibajide.policyintelligence.document.infrastructure.DocumentVersionRepository;
import com.shibajide.policyintelligence.embedding.application.EmbeddingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class DocumentIngestionService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentChunkRepository chunkRepository;
    private final CorpusStateRepository corpusStateRepository;
    private final DocumentContentExtractor contentExtractor;
    private final ChunkerRegistry chunkerRegistry;
    private final EmbeddingService embeddingService;

    public DocumentIngestionService(
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            DocumentChunkRepository chunkRepository,
            CorpusStateRepository corpusStateRepository,
            DocumentContentExtractor contentExtractor,
            ChunkerRegistry chunkerRegistry,
            EmbeddingService embeddingService
    ) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.chunkRepository = chunkRepository;
        this.corpusStateRepository = corpusStateRepository;
        this.contentExtractor = contentExtractor;
        this.chunkerRegistry = chunkerRegistry;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public DocumentIngestionResult create(IngestDocumentCommand command) {
        validate(command);
        var document = documentRepository.saveAndFlush(new Document(
                command.title().trim(),
                command.tenantId(),
                command.department(),
                command.region(),
                command.documentType(),
                command.classification()
        ));
        return createVersion(document, command);
    }

    @Transactional
    public DocumentIngestionResult createVersion(UUID documentId, IngestDocumentCommand command) {
        validate(command);
        var document = documentRepository.findLockedById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        return createVersion(document, command);
    }

    private DocumentIngestionResult createVersion(Document document, IngestDocumentCommand command) {
        String hash = sha256(command.content());
        versionRepository.findTopByDocumentIdOrderByVersionNumberDesc(document.getId())
                .filter(latest -> latest.getContentHash().equals(hash))
                .ifPresent(latest -> {
                    throw new DuplicateDocumentVersionException();
                });

        String text = contentExtractor.extract(
                command.originalFilename(),
                command.mediaType(),
                command.content()
        );
        var chunker = chunkerRegistry.get(command.chunkingStrategy());
        var texts = chunker.chunk(text, command.chunkSize(), effectiveOverlap(command));
        int versionNumber = versionRepository.countByDocumentId(document.getId()) + 1;

        var version = versionRepository.saveAndFlush(new DocumentVersion(
                document.getId(),
                versionNumber,
                command.originalFilename(),
                command.mediaType(),
                hash,
                command.chunkingStrategy(),
                command.chunkSize(),
                effectiveOverlap(command)
        ));

        chunkRepository.deactivateActiveChunks(document.getId());
        var chunks = IntStream.range(0, texts.size())
                .mapToObj(index -> new DocumentChunk(
                        document.getId(),
                        version.getId(),
                        index,
                        texts.get(index),
                        Map.of(
                                "originalFilename", command.originalFilename(),
                                "version", versionNumber,
                                "chunkingStrategy", command.chunkingStrategy().name(),
                                "tenantId", document.getTenantId(),
                                "department", document.getDepartment() == null ? "" : document.getDepartment(),
                                "region", document.getRegion() == null ? "" : document.getRegion(),
                                "documentType", document.getDocumentType() == null ? "" : document.getDocumentType(),
                                "classification", document.getClassification() == null ? "" : document.getClassification()
                        )
                ))
                .toList();
        chunkRepository.saveAllAndFlush(chunks);
        embeddingService.embedPendingChunks();
        document.touch();
        long corpusVersion = corpusStateRepository.lockSingleton().increment();

        return new DocumentIngestionResult(
                document.getId(),
                version.getId(),
                versionNumber,
                chunks.size(),
                corpusVersion
        );
    }

    private int effectiveOverlap(IngestDocumentCommand command) {
        return command.chunkingStrategy() == com.shibajide.policyintelligence.document.domain.ChunkingStrategy.FIXED_SIZE
                ? 0
                : command.chunkOverlap();
    }

    private void validate(IngestDocumentCommand command) {
        if (command.title() == null || command.title().isBlank() || command.title().length() > 300) {
            throw new IllegalArgumentException("Title must contain between 1 and 300 characters");
        }
        if (command.originalFilename() == null || command.originalFilename().isBlank()) {
            throw new IllegalArgumentException("Original filename is required");
        }
        if (command.chunkingStrategy() == null) {
            throw new IllegalArgumentException("Chunking strategy is required");
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
