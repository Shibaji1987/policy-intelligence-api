package com.acme.policyintelligence.document.infrastructure;

import com.acme.policyintelligence.document.application.DocumentContentExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
public class PdfAndTextContentExtractor implements DocumentContentExtractor {

    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md", "markdown");

    @Override
    public String extract(String filename, String mediaType, byte[] content) {
        if (content.length == 0) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }
        String extension = extensionOf(filename);
        if ("pdf".equals(extension) || "application/pdf".equalsIgnoreCase(mediaType)) {
            return extractPdf(content);
        }
        if (TEXT_EXTENSIONS.contains(extension) || isTextMediaType(mediaType)) {
            return new String(content, StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("Only PDF, TXT, and Markdown files are supported");
    }

    private String extractPdf(byte[] content) {
        try (var document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not extract text from PDF", exception);
        }
    }

    private boolean isTextMediaType(String mediaType) {
        return mediaType != null
                && (mediaType.toLowerCase(Locale.ROOT).startsWith("text/")
                || mediaType.equalsIgnoreCase("application/markdown"));
    }

    private String extensionOf(String filename) {
        int separator = filename == null ? -1 : filename.lastIndexOf('.');
        return separator < 0 ? "" : filename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }
}
