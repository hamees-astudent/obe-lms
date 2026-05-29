package com.lms.infrastructure.storage;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

/**
 * Port (interface) for object storage operations.
 *
 * <p>The only implementation shipped is {@link S3StorageService}, which targets
 * MinIO (or any S3-compatible endpoint). Swap the implementation in tests with
 * an in-memory stub without touching service-layer code.
 *
 * <h3>Key convention</h3>
 * Object keys follow the pattern: {@code <module>/<entityId>/<filename>}
 * <br>Examples:
 * <ul>
 *   <li>{@code submissions/3f4a.../report.pdf}</li>
 *   <li>{@code materials/9b1c.../lecture-1.mp4}</li>
 *   <li>{@code transcripts/7e2d.../transcript-2025.pdf}</li>
 * </ul>
 */
public interface StorageService {

    /**
     * Upload a stream to the bucket under the given key.
     *
     * @param objectKey     full object key (path within bucket)
     * @param content       data to upload — caller is responsible for closing the stream
     * @param contentLength byte length of {@code content}; required by S3 protocol
     * @param contentType   MIME type, e.g. {@code application/pdf}
     * @return the {@code objectKey} that was stored (for convenience)
     */
    String upload(String objectKey, InputStream content, long contentLength, String contentType);

    /**
     * Generate a time-limited pre-signed GET URL for the object.
     *
     * @param objectKey full object key
     * @param validity  how long the URL should remain valid
     * @return pre-signed URL that allows unauthenticated downloads
     */
    URL generatePresignedUrl(String objectKey, Duration validity);

    /**
     * Open a readable stream for the object.
     *
     * <p>The caller <b>must</b> close the returned stream to release the HTTP connection.
     *
     * @param objectKey full object key
     * @return input stream over the object's content
     */
    InputStream download(String objectKey);

    /**
     * Delete an object from the bucket. No-ops if the key does not exist.
     *
     * @param objectKey full object key
     */
    void delete(String objectKey);
}
