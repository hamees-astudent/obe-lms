package com.lms.modules.exams.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/** A student on the offering's roster, for the manual-entry picker. */
@Value
@Builder
public class RosterEntryResponse {
    UUID studentId;
    String name;
    String studentNumber;
    /** True when this student already has confirmed marks for the exam. */
    boolean hasResult;
}
