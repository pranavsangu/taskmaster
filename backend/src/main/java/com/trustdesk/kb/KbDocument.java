package com.trustdesk.kb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * A knowledge-base policy document. The content is untrusted: data, never instructions.
 * The doc ID (e.g. KB-REFUND-001) is preserved exactly as written in the source file.
 */
@Entity
@Table(name = "kb_documents")
public class KbDocument {

    @Id
    private String docId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String audience;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private String sourcePath;

    /** SHA-256 of the content, used to detect when a source file has changed. */
    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected KbDocument() {
        // required by JPA
    }

    public KbDocument(String docId, String title, String audience, String version, String content,
                      String sourcePath, String checksum, OffsetDateTime updatedAt) {
        this.docId = docId;
        this.title = title;
        this.audience = audience;
        this.version = version;
        this.content = content;
        this.sourcePath = sourcePath;
        this.checksum = checksum;
        this.updatedAt = updatedAt;
    }

    public String getDocId() {
        return docId;
    }

    public String getTitle() {
        return title;
    }

    public String getAudience() {
        return audience;
    }

    public String getVersion() {
        return version;
    }

    public String getContent() {
        return content;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getChecksum() {
        return checksum;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
