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

    /** HTTP(S) endpoint the server uses, e.g. {@code http://localhost:9000} for local MinIO. */
    private String endpoint;

    /**
     * The endpoint as browsers reach it, used to sign pre-signed URLs, e.g.
     * {@code https://files.example.edu}. A URL signed for {@code localhost}
     * only works on the server itself, so set this whenever the app is used
     * from other machines. Blank means {@link #endpoint}.
     */
    private String publicEndpoint;

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
