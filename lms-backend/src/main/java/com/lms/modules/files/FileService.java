package com.lms.modules.files;

import com.lms.infrastructure.storage.StorageProperties;
import com.lms.infrastructure.storage.StorageService;
import com.lms.modules.files.dto.PresignedUrlResponse;
import com.lms.modules.files.dto.UploadedFileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class FileService {

    private final StorageService        storageService;
    private final StorageProperties     storageProps;
    private final FileUploadRepository  fileRepo;

    // ── Upload ───────────────────────────────────────────────────────────────

    /**
     * Upload a multipart file to S3/MinIO and record its metadata.
     *
     * @param file      the uploaded file
     * @param uploaderId UUID of the authenticated user performing the upload
     * @param context   optional owner context, e.g. {@code "submissions"}
     * @param contextId optional ID of the owning entity
     * @return metadata of the stored file
     */
    public UploadedFileResponse upload(MultipartFile file,
                                       UUID uploaderId,
                                       String context,
                                       UUID contextId) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }

        UUID   keyId        = UUID.randomUUID();   // UUID embedded in the object key
        String safeName      = sanitizeFilename(file.getOriginalFilename());
        String ctx           = StringUtils.hasText(context) ? context : "files";
        String objectKey     = ctx + "/" + keyId + "/" + safeName;
        String contentType   = resolveContentType(file);

        try (InputStream in = file.getInputStream()) {
            storageService.upload(objectKey, in, file.getSize(), contentType);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read uploaded file: " + e.getMessage());
        }

        FileUpload entity = new FileUpload();
        // ID is assigned by @GeneratedValue(UUID) on persist
        entity.setObjectKey(objectKey);
        entity.setOriginalName(safeName);
        entity.setContentType(contentType);
        entity.setFileSize(file.getSize());
        entity.setUploadedBy(uploaderId);
        entity.setContext(ctx);
        entity.setContextId(contextId);

        fileRepo.save(entity);
        log.info("File uploaded: key={} size={} uploader={}", objectKey, file.getSize(), uploaderId);
        return toResponse(entity);
    }

    // ── Presigned URL ────────────────────────────────────────────────────────

    /**
     * Generate a time-limited pre-signed GET URL for the file.
     *
     * @param id            file record ID
     * @param expiryMinutes how long the URL is valid (1–1440 minutes)
     */
    @Transactional(readOnly = true)
    public PresignedUrlResponse generatePresignedUrl(UUID id, int expiryMinutes) {
        FileUpload file = findOrThrow(id);
        int clamped     = Math.max(1, Math.min(expiryMinutes, 1440));
        Duration validity = Duration.ofMinutes(clamped);

        URL url = storageService.generatePresignedUrl(file.getObjectKey(), validity);

        return PresignedUrlResponse.builder()
                .url(url.toExternalForm())
                .expiresAt(Instant.now().plus(validity))
                .originalName(file.getOriginalName())
                .contentType(file.getContentType())
                .fileSize(file.getFileSize())
                .build();
    }

    // ── Direct download stream ───────────────────────────────────────────────

    /**
     * Open an {@link InputStream} over the stored object.
     * The caller <b>must</b> close the stream.
     */
    @Transactional(readOnly = true)
    public DownloadResult download(UUID id) {
        FileUpload file = findOrThrow(id);
        InputStream stream = storageService.download(file.getObjectKey());
        return new DownloadResult(stream, file.getOriginalName(),
                file.getContentType(), file.getFileSize());
    }

    /** Thin container for a streaming download. */
    public record DownloadResult(InputStream stream, String filename,
                                  String contentType, long fileSize) {}

    // ── Delete ───────────────────────────────────────────────────────────────

    /**
     * Delete the object from storage and remove the metadata record.
     * Only the uploader or an ADMIN may delete.
     *
     * @param id          file record ID
     * @param requesterId the caller's user ID
     * @param isAdmin     whether the caller has ADMIN role
     */
    public void delete(UUID id, UUID requesterId, boolean isAdmin) {
        FileUpload file = findOrThrow(id);
        if (!isAdmin && !file.getUploadedBy().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have permission to delete this file");
        }
        storageService.delete(file.getObjectKey());
        fileRepo.deleteById(id);
        log.info("File deleted: key={} by={}", file.getObjectKey(), requesterId);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UploadedFileResponse getFile(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<UploadedFileResponse> listByContext(String context, UUID contextId) {
        return fileRepo.findAllByContextAndContextId(context, contextId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private FileUpload findOrThrow(UUID id) {
        return fileRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "File not found: " + id));
    }

    /**
     * Sanitize a filename to prevent path traversal and ensure S3 key safety.
     * Keeps alphanumerics, dots, hyphens, and underscores; replaces everything
     * else with underscores.
     */
    static String sanitizeFilename(String name) {
        if (!StringUtils.hasText(name)) {
            return "upload";
        }
        // Strip any directory separators first
        String base = name.replaceAll("[/\\\\]", "_");
        // Replace unsafe characters
        String safe = base.replaceAll("[^A-Za-z0-9._\\-]", "_");
        // Collapse consecutive underscores for readability
        return safe.replaceAll("_{2,}", "_");
    }

    /** Use the provided content type if non-blank; fall back to octet-stream. */
    private String resolveContentType(MultipartFile file) {
        String ct = file.getContentType();
        return StringUtils.hasText(ct) ? ct : "application/octet-stream";
    }

    private UploadedFileResponse toResponse(FileUpload f) {
        return UploadedFileResponse.builder()
                .id(f.getId())
                .objectKey(f.getObjectKey())
                .originalName(f.getOriginalName())
                .contentType(f.getContentType())
                .fileSize(f.getFileSize())
                .uploadedBy(f.getUploadedBy())
                .context(f.getContext())
                .contextId(f.getContextId())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
