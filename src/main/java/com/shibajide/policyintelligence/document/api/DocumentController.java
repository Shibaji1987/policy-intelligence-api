package com.shibajide.policyintelligence.document.api;

import com.shibajide.policyintelligence.document.application.DocumentChunkSummary;
import com.shibajide.policyintelligence.document.application.DocumentIngestionResult;
import com.shibajide.policyintelligence.document.application.DocumentIngestionService;
import com.shibajide.policyintelligence.document.application.DocumentQueryService;
import com.shibajide.policyintelligence.document.application.DocumentSummary;
import com.shibajide.policyintelligence.document.application.DocumentVersionSummary;
import com.shibajide.policyintelligence.document.application.IngestDocumentCommand;
import com.shibajide.policyintelligence.document.domain.ChunkingStrategy;
import com.shibajide.policyintelligence.security.RetrievalAccessPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents", description = "Document ingestion, immutable versions, and chunk inspection")
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final DocumentQueryService queryService;
    private final RetrievalAccessPolicy accessPolicy;
    private final Tika tika;

    public DocumentController(
            DocumentIngestionService ingestionService,
            DocumentQueryService queryService,
            RetrievalAccessPolicy accessPolicy,
            Tika tika
    ) {
        this.ingestionService = ingestionService;
        this.queryService = queryService;
        this.accessPolicy = accessPolicy;
        this.tika = tika;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a document and its first immutable version")
    public DocumentIngestionResult create(
            @RequestParam String title,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String classification,
            @RequestParam(defaultValue = "FIXED_SIZE") ChunkingStrategy strategy,
            @RequestParam(defaultValue = "1000") int chunkSize,
            @RequestParam(defaultValue = "200") int overlap
    ) {
        return ingestionService.create(toCommand(title, file, accessPolicy.ingestionTenant(tenantId), department, region, documentType, classification, strategy, chunkSize, overlap));
    }

    @PostMapping(path = "/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new immutable version for an existing document")
    public DocumentIngestionResult createVersion(
            @PathVariable UUID documentId,
            @RequestParam(required = false) String title,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) String classification,
            @RequestParam(defaultValue = "FIXED_SIZE") ChunkingStrategy strategy,
            @RequestParam(defaultValue = "1000") int chunkSize,
            @RequestParam(defaultValue = "200") int overlap
    ) {
        String effectiveTitle = title == null || title.isBlank() ? "Version update for " + documentId : title;
        return ingestionService.createVersion(
                documentId,
                toCommand(effectiveTitle, file, accessPolicy.ingestionTenant(tenantId), department, region, documentType, classification, strategy, chunkSize, overlap)
        );
    }

    @GetMapping
    @Operation(summary = "List documents with pagination")
    public Page<DocumentSummary> findDocuments(@PageableDefault(size = 25) Pageable pageable) {
        return queryService.findDocuments(pageable);
    }

    @GetMapping("/{documentId}/versions")
    @Operation(summary = "List immutable versions for a document")
    public List<DocumentVersionSummary> findVersions(@PathVariable UUID documentId) {
        return queryService.findVersions(documentId);
    }

    @GetMapping("/versions/{versionId}/chunks")
    @Operation(summary = "List chunks for a document version")
    public List<DocumentChunkSummary> findChunks(@PathVariable UUID versionId) {
        return queryService.findChunks(versionId);
    }

    private IngestDocumentCommand toCommand(
            String title,
            MultipartFile file,
            String tenantId,
            String department,
            String region,
            String documentType,
            String classification,
            ChunkingStrategy strategy,
            int chunkSize,
            int overlap
    ) {
        try {
            return new IngestDocumentCommand(
                    title,
                    file.getOriginalFilename() == null ? "uploaded-file" : file.getOriginalFilename(),
                    detectMediaType(file),
                    file.getBytes(),
                    tenantId,
                    department,
                    region,
                    documentType,
                    classification,
                    strategy,
                    chunkSize,
                    overlap
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read uploaded file", exception);
        }
    }

    private String detectMediaType(MultipartFile file) throws IOException {
        String detected = tika.detect(file.getBytes(), file.getOriginalFilename());
        return detected == null || detected.isBlank() ? MediaType.APPLICATION_OCTET_STREAM_VALUE : detected;
    }
}
