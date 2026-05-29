package com.lms.modules.assessment.dto;

import lombok.Data;

@Data
public class SubmitAssignmentRequest {

    /** Inline content for TEXT / BOTH type assignments. */
    private String textContent;

    /** S3/MinIO object key from a prior file upload (FILE / BOTH type assignments). */
    private String fileKey;

    private String fileName;

    private Long fileSize;
}
