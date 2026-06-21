package com.acme.policyintelligence.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "document")
public class Document {

    @Id
    private UUID id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId;

    @Column(length = 120)
    private String department;

    @Column(length = 120)
    private String region;

    @Column(name = "document_type", length = 120)
    private String documentType;

    @Column(length = 120)
    private String classification;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Document() {
    }

    public Document(String title, String tenantId, String department, String region, String documentType, String classification) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        this.id = UUID.randomUUID();
        this.title = title;
        this.tenantId = normalizeTenant(tenantId);
        this.department = blankToNull(department);
        this.region = blankToNull(region);
        this.documentType = blankToNull(documentType);
        this.classification = blankToNull(classification);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void touch() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getDepartment() {
        return department;
    }

    public String getRegion() {
        return region;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getClassification() {
        return classification;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    private String normalizeTenant(String value) {
        return value == null || value.isBlank() ? "default" : value.strip();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
