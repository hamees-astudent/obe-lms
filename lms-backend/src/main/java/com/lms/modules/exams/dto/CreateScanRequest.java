package com.lms.modules.exams.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * A captured exam page submitted for reading.
 *
 * <p>The image is uploaded to {@code POST /api/files} first and referenced here
 * by its object key, matching how assignment submissions carry a file. That
 * keeps the page stored before the slow extraction call runs, so a failed or
 * timed-out read never loses the photograph.
 */
@Data
public class CreateScanRequest {

    /** S3/MinIO object key from a prior upload to {@code /api/files}. */
    @NotBlank
    @Size(max = 512)
    private String imageKey;

    private String imageName;

    private Long imageSize;

    /**
     * The exam this copy belongs to. Optional: when omitted the exam is resolved
     * from the course code and date read off the page, among the exams the
     * caller actually teaches.
     */
    private UUID examId;
}
