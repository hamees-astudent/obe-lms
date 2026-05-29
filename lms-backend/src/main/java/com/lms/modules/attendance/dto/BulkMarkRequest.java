package com.lms.modules.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkMarkRequest(

        @NotEmpty
        @Valid
        List<BulkMarkEntry> records
) {}
