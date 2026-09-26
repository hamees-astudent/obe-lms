package com.lms.modules.timetable;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One weekly class meeting of an offering: the day, the hours and the room.
 * It recurs every week of the offering's semester.
 */
@Entity
@Table(name = "timetable_entries")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TimetableEntry extends BaseEntity {

    /** FK → program_semester_courses.id (courses module) — stored as plain UUID. */
    @Column(name = "psc_id", nullable = false)
    private UUID pscId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SessionKind kind;

    /** ISO day of week, 1 = Monday. */
    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
