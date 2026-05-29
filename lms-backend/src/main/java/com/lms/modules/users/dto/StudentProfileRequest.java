package com.lms.modules.users.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record StudentProfileRequest(

        @Size(max = 30)
        String studentNumber,

        LocalDate dateOfBirth,

        @Size(max = 30)
        String phone,

        String address,

        LocalDate enrollmentDate,

        /** S3/MinIO object key — set by the files module after upload. */
        String profilePictureKey
) {}
