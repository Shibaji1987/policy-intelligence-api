package com.shibajide.policyintelligence.document.application;

import com.shibajide.policyintelligence.chunking.ChunkerRegistry;
import com.shibajide.policyintelligence.document.domain.Document;
import com.shibajide.policyintelligence.document.domain.DocumentChunk;
import com.shibajide.policyintelligence.document.domain.DocumentVersion;
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
    private final CorpusVersionService corpusVersionService;
    private final DocumentContentExtractor contentExtractor;
    private final ChunkerRegistry chunkerRegistry;
    private final EmbeddingService embeddingService;

    public DocumentIngestionService(
            DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository,
            DocumentChunkRepository chunkRepository,
            CorpusVersionService corpusVersionService,
            DocumentContentExtractor contentExtractor,
            ChunkerRegistry chunkerRegistry,
            EmbeddingService embeddingService
    ) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.chunkRepository = chunkRepository;
        this.corpusVersionService = corpusVersionService;
        this.contentExtractor = contentExtractor;
        this.chunkerRegistry = chunkerRegistry;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public DocumentIngestionResult create(IngestDocumentCommand command) {
        validate(command);
        Document document = createDocument(command);
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
        IngestionPlan plan = plan(document, command);
        DocumentVersion version = saveVersion(plan);
        var chunks = replaceActiveChunks(plan, version);

        finalizeIngestion(document);
        long corpusVersion = corpusVersionService.increment(document.getTenantId());
        return result(document, version, plan.versionNumber(), chunks.size(), corpusVersion);
    }

    private Document createDocument(IngestDocumentCommand command) {
        return documentRepository.saveAndFlush(new Document(
                command.title().trim(),
                command.tenantId(),
                command.department(),
                command.region(),
                command.documentType(),
                command.classification()
        ));
    }

    private IngestionPlan plan(Document document, IngestDocumentCommand command) {
        String hash = sha256(command.content());
        ensureNewContent(document, hash);
        return new IngestionPlan(
                document,
                command,
                hash,
                extractChunks(command),
                versionRepository.countByDocumentId(document.getId()) + 1,
                effectiveOverlap(command)
        );
    }

    private void ensureNewContent(Document document, String hash) {
        versionRepository.findTopByDocumentIdOrderByVersionNumberDesc(document.getId())
                .filter(latest -> latest.getContentHash().equals(hash))
                .ifPresent(latest -> {
                    throw new DuplicateDocumentVersionException();
                });
    }

    private java.util.List<String> extractChunks(IngestDocumentCommand command) {
        String text = contentExtractor.extract(
                command.originalFilename(),
                command.mediaType(),
                command.content()
        );
        return chunkerRegistry.get(command.chunkingStrategy())
                .chunk(text, command.chunkSize(), effectiveOverlap(command));
    }

    private DocumentVersion saveVersion(IngestionPlan plan) {
        return versionRepository.saveAndFlush(new DocumentVersion(
                plan.document().getId(),
                plan.versionNumber(),
                plan.command().originalFilename(),
                plan.command().mediaType(),
                plan.contentHash(),
                plan.command().chunkingStrategy(),
                plan.command().chunkSize(),
                plan.overlap()
        ));
    }

    private java.util.List<DocumentChunk> replaceActiveChunks(IngestionPlan plan, DocumentVersion version) {
        chunkRepository.deactivateActiveChunks(plan.document().getId());
        var chunks = IntStream.range(0, plan.chunkTexts().size())
                .mapToObj(index -> chunk(plan, version, index))
                .toList();
        chunkRepository.saveAllAndFlush(chunks);
        return chunks;
    }

    private DocumentChunk chunk(IngestionPlan plan, DocumentVersion version, int index) {
        return new DocumentChunk(
                plan.document().getId(),
                version.getId(),
                index,
                plan.chunkTexts().get(index),
                metadata(plan)
        );
    }

    private Map<String, Object> metadata(IngestionPlan plan) {
        Document document = plan.document();
        return Map.of(
                "originalFilename", plan.command().originalFilename(),
                "version", plan.versionNumber(),
                "chunkingStrategy", plan.command().chunkingStrategy().name(),
                "tenantId", document.getTenantId(),
                "department", nullToEmpty(document.getDepartment()),
                "region", nullToEmpty(document.getRegion()),
                "documentType", nullToEmpty(document.getDocumentType()),
                "classification", nullToEmpty(document.getClassification())
        );
    }

    private void finalizeIngestion(Document document) {
        embeddingService.embedPendingChunks();
        document.touch();
    }

    private DocumentIngestionResult result(
            Document document,
            DocumentVersion version,
            int versionNumber,
            int chunkCount,
            long corpusVersion
    ) {
        return new DocumentIngestionResult(
                document.getId(),
                version.getId(),
                versionNumber,
                chunkCount,
                corpusVersion
        );
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
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

    private record IngestionPlan(
            Document document,
            IngestDocumentCommand command,
            String contentHash,
            java.util.List<String> chunkTexts,
            int versionNumber,
            int overlap
    ) {
    }
}
