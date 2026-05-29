package com.lms.modules.files.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class PresignedUrlResponse {
    /** The time-limited download URL. */
    String url;
    /** When the URL expires (ISO-8601 UTC). */
    Instant expiresAt;
    String  originalName;
    String  contentType;
    long    fileSize;
}
