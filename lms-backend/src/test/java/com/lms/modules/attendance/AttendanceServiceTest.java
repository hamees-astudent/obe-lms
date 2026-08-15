package com.lms.modules.attendance;

import com.lms.infrastructure.messaging.KafkaEventPublisher;
import com.lms.modules.attendance.dto.BulkMarkEntry;
import com.lms.modules.attendance.dto.BulkMarkRequest;
import com.lms.modules.attendance.dto.CreateSessionRequest;
import com.lms.modules.attendance.dto.MarkAttendanceRequest;
import com.lms.shared.CacheNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.lms.infrastructure.cache.CacheEvictor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the two defects that made attendance look like it was not being
 * tracked at all: a cached summary that was never invalidated, and writes that
 * any teacher could perform on any offering.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceServiceTest {

    @Mock  private AttendanceSessionRepository sessionRepository;
    @Mock  private AttendanceRecordRepository  recordRepository;
    @Mock  private KafkaEventPublisher         kafkaEventPublisher;
    @Mock  private CacheEvictor               cacheEvictor;

    private final AttendanceProperties properties = new AttendanceProperties();

    @InjectMocks private AttendanceService service;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID pscId     = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AttendanceService(sessionRepository, recordRepository,
                kafkaEventPublisher, properties, cacheEvictor);

        when(sessionRepository.isStaffOfOffering(any(), any())).thenReturn(true);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(openSession()));
        when(recordRepository.findBySessionIdAndStudentId(any(), any())).thenReturn(Optional.empty());
        when(recordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(recordRepository.computeSummary(any(), any())).thenReturn(summaryView(1, 1));
    }

    // ── Cache invalidation ───────────────────────────────────────────────────

    @Test
    @DisplayName("marking a record evicts that student's cached summary")
    void markRecordEvictsSummary() {
        service.markRecord(sessionId, studentId,
                new MarkAttendanceRequest("PRESENT", null), teacherId, false);

        verify(cacheEvictor).evict(CacheNames.ATTENDANCE_SUMMARY, pscId + ":" + studentId);
    }

    @Test
    @DisplayName("bulk marking evicts the cached summary of every student touched")
    void bulkMarkEvictsEverySummary() {
        UUID otherStudent = UUID.randomUUID();
        service.bulkMark(sessionId,
                new BulkMarkRequest(List.of(
                        new BulkMarkEntry(studentId, "PRESENT", null),
                        new BulkMarkEntry(otherStudent, "ABSENT", null))),
                teacherId, false);

        verify(cacheEvictor).evict(CacheNames.ATTENDANCE_SUMMARY, pscId + ":" + studentId);
        verify(cacheEvictor).evict(CacheNames.ATTENDANCE_SUMMARY, pscId + ":" + otherStudent);
    }

    // ── Summary arithmetic ───────────────────────────────────────────────────

    @Test
    @DisplayName("a student with nothing marked reads 0%, not 100%")
    void summaryWithNoRecordsIsZero() {
        when(recordRepository.computeSummary(pscId, studentId)).thenReturn(summaryView(0, 0));

        var summary = service.getAttendanceSummary(pscId, studentId);

        assertThat(summary.total()).isZero();
        assertThat(summary.percentage()).isZero();
    }

    @Test
    @DisplayName("no threshold alert fires when nothing has been marked")
    void noAlertWithoutRecords() {
        when(recordRepository.computeSummary(pscId, studentId)).thenReturn(summaryView(0, 0));

        service.checkThresholdAndAlert(pscId, studentId);

        verify(kafkaEventPublisher, never()).publishAttendanceAlertEvent(any());
    }

    // ── Offering ownership ───────────────────────────────────────────────────

    @Test
    @DisplayName("a teacher who does not run the offering cannot open a session")
    void nonStaffCannotCreateSession() {
        when(sessionRepository.isStaffOfOffering(eq(pscId), eq(teacherId))).thenReturn(false);

        assertThatThrownBy(() -> service.createSession(teacherId, false,
                new CreateSessionRequest(pscId, LocalDate.now(), "Week 1")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a teacher who does not run the offering cannot mark attendance")
    void nonStaffCannotMark() {
        when(sessionRepository.isStaffOfOffering(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.markRecord(sessionId, studentId,
                new MarkAttendanceRequest("PRESENT", null), teacherId, false))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("an admin may mark attendance on any offering")
    void adminBypassesOwnership() {
        when(sessionRepository.isStaffOfOffering(any(), any())).thenReturn(false);

        var response = service.markRecord(sessionId, studentId,
                new MarkAttendanceRequest("PRESENT", null), teacherId, true);

        assertThat(response.status()).isEqualTo("PRESENT");
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private AttendanceSession openSession() {
        var session = new AttendanceSession();
        session.setPscId(pscId);
        session.setSessionDate(LocalDate.now());
        return session;
    }

    private AttendanceSummaryView summaryView(long attended, long total) {
        return new AttendanceSummaryView() {
            @Override public long getAttended() { return attended; }
            @Override public long getTotal()    { return total; }
        };
    }
}
