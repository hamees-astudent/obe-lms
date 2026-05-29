package com.lms.modules.assessment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assignment_clo_mappings")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class AssignmentCloMapping {

    @EmbeddedId
    private AssignmentCloMappingId id = new AssignmentCloMappingId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("assignmentId")
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    /** Relative contribution weight; null = equal weighting. */
    @Column(precision = 5, scale = 2)
    private BigDecimal weight;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AssignmentCloMapping of(Assignment assignment, UUID cloId, BigDecimal weight) {
        AssignmentCloMapping m = new AssignmentCloMapping();
        m.getId().setAssignmentId(assignment.getId());
        m.getId().setCloId(cloId);
        m.setAssignment(assignment);
        m.setWeight(weight);
        return m;
    }
}
