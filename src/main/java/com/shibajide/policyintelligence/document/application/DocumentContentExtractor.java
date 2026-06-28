package com.shibajide.policyintelligence.document.application;

public interface DocumentContentExtractor {

    String extract(String filename, String mediaType, byte[] content);
}
