package com.lms.modules.transcript;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "grading_scales")
@Getter
@Setter
@NoArgsConstructor
public class GradingScale extends BaseEntity {

    /**
     * FK → programs.id (programs module) — plain UUID.
     * Null = system-wide global scale.
     */
    @Column(name = "program_id")
    private UUID programId;

    @Column(nullable = false, length = 120)
    private String name;

    /** Marks this as the active scale for its scope (program or global). */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
