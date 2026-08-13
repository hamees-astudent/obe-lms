package com.lms.modules.courses;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Links a {@link CourseMaterial} to the {@link Clo} it teaches toward.
 *
 * <p>Completes the OBE chain: material → CLO → PLO. Assessments already had
 * their CLO links; teaching material did not, which left no way to answer
 * "which lecture covers this outcome?" or to spot a CLO nothing teaches.
 */
@Entity
@Table(name = "material_clo_mappings")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MaterialCloMapping {

    @EmbeddedId
    private MaterialCloMappingId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("materialId")
    @JoinColumn(name = "material_id")
    private CourseMaterial material;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("cloId")
    @JoinColumn(name = "clo_id")
    private Clo clo;

    /** Contribution weight 0–100 %, nullable (qualitative-only mapping when null). */
    @Column(precision = 5, scale = 2)
    private BigDecimal weight;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
