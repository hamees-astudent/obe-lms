package com.lms.modules.timetable;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/** A teaching space the timetable can place classes in. */
@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Room extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 80)
    private String building;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SessionKind kind = SessionKind.LECTURE;

    /** Inactive rooms keep the classes already in them but get no new ones. */
    @Column(nullable = false)
    private boolean active = true;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
