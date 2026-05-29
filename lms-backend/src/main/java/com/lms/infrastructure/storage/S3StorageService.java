package com.lms.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

/**
 * {@link StorageService} implementation backed by an S3-compatible endpoint
 * (MinIO in development, AWS S3 in production).
 *
 * <p>All calls are synchronous (blocking). For large-file scenarios the
 * {@code s3-transfer-manager} dependency is on the classpath if async/multipart
 * uploads are needed later.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private final S3Client      s3Client;
    private final S3Presigner   s3Presigner;
    private final StorageProperties props;

    // ── StorageService ────────────────────────────────────────────────────────

    @Override
    public String upload(String objectKey,
                         InputStream content,
                         long contentLength,
                         String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(props.getBucketName())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(content, contentLength));
        log.debug("Uploaded object: bucket={} key={} size={}", props.getBucketName(), objectKey, contentLength);
        return objectKey;
    }

    @Override
    public URL generatePresignedUrl(String objectKey, Duration validity) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(validity)
                .getObjectRequest(r -> r
                        .bucket(props.getBucketName())
                        .key(objectKey))
                .build();

        URL url = s3Presigner.presignGetObject(presignRequest).url();
        log.debug("Generated pre-signed URL: key={} validity={}", objectKey, validity);
        return url;
    }

    @Override
    public InputStream download(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(props.getBucketName())
                .key(objectKey)
                .build();

        // ResponseInputStream implements InputStream — caller must close it
        return s3Client.getObject(request);
    }

    @Override
    public void delete(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(props.getBucketName())
                .key(objectKey)
                .build();

        s3Client.deleteObject(request);
        log.debug("Deleted object: bucket={} key={}", props.getBucketName(), objectKey);
    }
}
