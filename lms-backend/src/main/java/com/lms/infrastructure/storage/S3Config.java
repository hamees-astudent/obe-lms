package com.lms.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Configures the AWS SDK v2 {@link S3Client} and {@link S3Presigner} beans
 * for use with MinIO (or any S3-compatible storage endpoint).
 *
 * <h3>MinIO specifics</h3>
 * <ul>
 *   <li>{@code endpointOverride} — points the client at the MinIO host instead of AWS.</li>
 *   <li>{@code pathStyleAccessEnabled(true)} — MinIO requires path-style URLs
 *       ({@code http://host/bucket/key}) rather than virtual-hosted
 *       ({@code http://bucket.host/key}).</li>
 * </ul>
 *
 * <h3>AWS S3 production use</h3>
 * Remove or conditionalise {@code endpointOverride} and {@code pathStyleAccessEnabled}
 * when deploying against real AWS; use IAM roles + instance profile credentials instead
 * of the static {@code access-key} / {@code secret-key} properties.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(StorageProperties.class)
public class S3Config {

    // ── S3Client ──────────────────────────────────────────────────────────────

    @Bean
    public S3Client s3Client(StorageProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .credentialsProvider(credentials(props))
                .region(Region.of(props.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)   // required for MinIO
                        .build())
                .build();
    }

    // ── S3Presigner ───────────────────────────────────────────────────────────

    @Bean
    public S3Presigner s3Presigner(StorageProperties props) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .credentialsProvider(credentials(props))
                .region(Region.of(props.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    // ── Bucket initialisation ─────────────────────────────────────────────────

    /**
     * Ensures the configured bucket exists on startup.
     * Creates it if missing — safe to run repeatedly (idempotent).
     */
    @Bean
    public CommandLineRunner ensureBucketExists(S3Client s3Client, StorageProperties props) {
        return args -> {
            String bucket = props.getBucketName();
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
                log.info("S3 bucket '{}' already exists", bucket);
            } catch (NoSuchBucketException e) {
                s3Client.createBucket(r -> r.bucket(bucket));
                log.info("Created S3 bucket '{}'", bucket);
            }
        };
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static StaticCredentialsProvider credentials(StorageProperties props) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey()));
    }
}
