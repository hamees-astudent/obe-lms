package com.lms.modules.courses;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseAssistantId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "psc_id")
    private UUID pscId;

    @Column(name = "user_id")
    private UUID userId;
}
