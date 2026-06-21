package com.acme.policyintelligence.document.api;

import com.acme.policyintelligence.document.application.DocumentChunkSummary;
import com.acme.policyintelligence.document.application.DocumentIngestionResult;
import com.acme.policyintelligence.document.application.DocumentIngestionService;
import com.acme.policyintelligence.document.application.DocumentQueryService;
import com.acme.policyintelligence.document.application.DocumentSummary;
import com.acme.policyintelligence.document.application.DocumentVersionSummary;
import com.acme.policyintelligence.document.application.IngestDocumentCommand;
import com.acme.policyintelligence.document.domain.ChunkingStrategy;
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
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final DocumentQueryService queryService;

    public DocumentController(
            DocumentIngestionService ingestionService,
            DocumentQueryService queryService
    ) {
        this.ingestionService = ingestionService;
        this.queryService = queryService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentIngestionResult create(
            @RequestParam String title,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "FIXED_SIZE") ChunkingStrategy strategy,
            @RequestParam(defaultValue = "1000") int chunkSize,
            @RequestParam(defaultValue = "200") int overlap
    ) {
        return ingestionService.create(toCommand(title, file, strategy, chunkSize, overlap));
    }

    @PostMapping(path = "/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentIngestionResult createVersion(
            @PathVariable UUID documentId,
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "FIXED_SIZE") ChunkingStrategy strategy,
            @RequestParam(defaultValue = "1000") int chunkSize,
            @RequestParam(defaultValue = "200") int overlap
    ) {
        String titlePlaceholder = "existing-document";
        return ingestionService.createVersion(
                documentId,
                toCommand(titlePlaceholder, file, strategy, chunkSize, overlap)
        );
    }

    @GetMapping
    public List<DocumentSummary> findDocuments() {
        return queryService.findDocuments();
    }

    @GetMapping("/{documentId}/versions")
    public List<DocumentVersionSummary> findVersions(@PathVariable UUID documentId) {
        return queryService.findVersions(documentId);
    }

    @GetMapping("/versions/{versionId}/chunks")
    public List<DocumentChunkSummary> findChunks(@PathVariable UUID versionId) {
        return queryService.findChunks(versionId);
    }

    private IngestDocumentCommand toCommand(
            String title,
            MultipartFile file,
            ChunkingStrategy strategy,
            int chunkSize,
            int overlap
    ) {
        try {
            return new IngestDocumentCommand(
                    title,
                    file.getOriginalFilename() == null ? "uploaded-file" : file.getOriginalFilename(),
                    file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType(),
                    file.getBytes(),
                    strategy,
                    chunkSize,
                    overlap
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read uploaded file", exception);
        }
    }
}
