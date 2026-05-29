package com.lms.modules.users.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TeacherProfileRequest(

        @Size(max = 30)
        String employeeNumber,

        @Size(max = 100)
        String department,

        @Size(max = 100)
        String designation,

        @Size(max = 30)
        String phone,

        LocalDate joiningDate,

        String bio,

        /** S3/MinIO object key — set by the files module after upload. */
        String profilePictureKey
) {}
