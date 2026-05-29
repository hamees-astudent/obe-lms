package com.lms.infrastructure.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the S3-compatible object storage backend (MinIO or AWS S3).
 * Bound from {@code app.files.storage.*} in {@code application.yml}.
 */
@Data
@ConfigurationProperties(prefix = "app.files.storage")
public class StorageProperties {

    /** HTTP(S) endpoint, e.g. {@code http://localhost:9000} for local MinIO. */
    private String endpoint;

    /** Access key / AWS access key ID. */
    private String accessKey;

    /** Secret key / AWS secret access key. */
    private String secretKey;

    /** Bucket where all LMS files are stored. */
    private String bucketName;

    /** AWS region string; use any valid value for MinIO (e.g. {@code us-east-1}). */
    private String region = "us-east-1";

    /**
     * Default validity for pre-signed GET URLs.
     * Override per call via {@link StorageService#generatePresignedUrl}.
     */
    private long presignedUrlExpiryMinutes = 60;
}
