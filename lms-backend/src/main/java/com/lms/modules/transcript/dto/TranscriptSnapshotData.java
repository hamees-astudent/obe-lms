package com.lms.modules.transcript.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The full snapshot stored as JSONB in the transcripts table.
 * Uses {@code @Data} + {@code @NoArgsConstructor} so Jackson can deserialize from
 * the JSONB map when building {@link TranscriptResponse}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptSnapshotData {
    private String studentName;
    private String studentNumber;
    private String programName;
    private String semesterName;
    private String gradingScaleName;

    private Double semesterGpa;
    private Double cumulativeGpa;
    private int    totalCreditHours;
    private int    earnedCreditHours;

    private List<CourseTranscriptDetail> courses;
    private List<PloAttainmentDetail>    ploAttainment;
}
