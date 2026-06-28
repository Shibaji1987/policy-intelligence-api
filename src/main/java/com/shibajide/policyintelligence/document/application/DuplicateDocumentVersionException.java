package com.shibajide.policyintelligence.document.application;

public class DuplicateDocumentVersionException extends RuntimeException {

    public DuplicateDocumentVersionException() {
        super("The uploaded content is identical to the latest document version");
    }
}
