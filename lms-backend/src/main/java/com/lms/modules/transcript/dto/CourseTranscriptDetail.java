package com.lms.modules.transcript.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class CourseTranscriptDetail {
    UUID   pscId;
    String courseCode;
    String courseName;
    int    creditHours;
    Double attendancePercentage;
    Double totalMarks;
    Double marksObtained;
    Double percentage;
    String gradeLetter;
    Double gradePoints;
    List<CloAttainmentDetail> cloAttainment;
}
