package com.lms.modules.files;

import com.lms.modules.files.FileService.DownloadResult;
import com.lms.modules.files.dto.PresignedUrlResponse;
import com.lms.modules.files.dto.UploadedFileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final FileUserResolver userResolver;

    // ── Upload ───────────────────────────────────────────────────────────────

    /**
     * Upload a single file.
     *
     * <p>Multipart parameters:
     * <ul>
     *   <li>{@code file}      — the file part (required)</li>
     *   <li>{@code context}   — owning module, e.g. {@code submissions} (optional)</li>
     *   <li>{@code contextId} — owning entity UUID (optional)</li>
     * </ul>
     * Roles: any authenticated user.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadedFileResponse upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String context,
            @RequestParam(required = false) UUID contextId,
            Authentication auth) {
        UUID uploaderId = userResolver.resolveId(auth.getName());
        return fileService.upload(file, uploaderId, context, contextId);
    }

    // ── Metadata ─────────────────────────────────────────────────────────────

    /**
     * Get file metadata by ID.
     * Roles: any authenticated user.
     */
    @GetMapping("/{id}")
    public UploadedFileResponse getFile(@PathVariable UUID id) {
        return fileService.getFile(id);
    }

    /**
     * List all files for a given context + entity.
     * Roles: any authenticated user.
     */
    @GetMapping
    public List<UploadedFileResponse> listByContext(
            @RequestParam String context,
            @RequestParam UUID contextId) {
        return fileService.listByContext(context, contextId);
    }

    // ── Presigned URL ─────────────────────────────────────────────────────────

    /**
     * Generate a time-limited pre-signed download URL.
     *
     * @param expiryMinutes validity in minutes (1–1440, default 60)
     * Roles: any authenticated user.
     */
    @GetMapping("/{id}/url")
    public PresignedUrlResponse getPresignedUrl(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "60") int expiryMinutes) {
        return fileService.generatePresignedUrl(id, expiryMinutes);
    }

    // ── Direct download ───────────────────────────────────────────────────────

    /**
     * Stream the file content directly.
     * Use {@code /url} instead for large files to avoid proxying through the app.
     * Roles: any authenticated user.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
        DownloadResult result = fileService.download(id);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(result.contentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.filename() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(result.fileSize()))
                .contentType(mediaType)
                .body(new InputStreamResource(result.stream()));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Delete a file. Only the uploader or an ADMIN may delete.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth) {
        UUID    requesterId = userResolver.resolveId(auth.getName());
        boolean isAdmin     = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        fileService.delete(id, requesterId, isAdmin);
    }
}
