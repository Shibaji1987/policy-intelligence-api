package com.acme.policyintelligence.document.application;

import java.util.UUID;

public record DocumentIngestionResult(
        UUID documentId,
        UUID versionId,
        int version,
        int chunkCount,
        long corpusVersion
) {
}
