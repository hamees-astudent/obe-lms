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
 * Many-to-many alignment of a {@link Clo} to a PLO (from the programs module).
 * The {@code plo_id} is stored as a plain UUID in {@link CloPloMappingId} to avoid
 * a cross-module ORM join.
 * <p>
 * {@code weight} is optional — a non-null value enables weighted PLO attainment computation.
 */
@Entity
@Table(name = "clo_plo_mappings")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CloPloMapping {

    @EmbeddedId
    private CloPloMappingId id;

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
