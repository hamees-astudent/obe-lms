package com.lms.modules.files;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Metadata record for every object stored in S3/MinIO.
 * The {@code object_key} is the authoritative reference passed to
 * {@link com.lms.infrastructure.storage.StorageService}.
 */
@Entity
@Table(name = "file_uploads")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class FileUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Full S3/MinIO object key — unique across the bucket. */
    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    /** The filename as provided by the client (displayed to users). */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    /** MIME type, e.g. {@code application/pdf}. */
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    /** File size in bytes. */
    @Column(name = "file_size", nullable = false)
    private long fileSize;

    /** FK → users.id — who uploaded the file. */
    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    /** Module that owns this file, e.g. {@code submissions}, {@code materials}. */
    @Column(name = "context", length = 50)
    private String context;

    /** ID of the owning entity within the context (polymorphic, no FK). */
    @Column(name = "context_id")
    private UUID contextId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
