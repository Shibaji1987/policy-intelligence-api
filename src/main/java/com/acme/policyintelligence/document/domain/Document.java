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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Document() {
    }

    public Document(String title) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        this.id = UUID.randomUUID();
        this.title = title;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
