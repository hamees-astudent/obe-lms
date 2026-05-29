package com.lms.modules.files.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class UploadedFileResponse {
    UUID          id;
    String        objectKey;
    String        originalName;
    String        contentType;
    long          fileSize;
    UUID          uploadedBy;
    String        context;
    UUID          contextId;
    LocalDateTime createdAt;
}
