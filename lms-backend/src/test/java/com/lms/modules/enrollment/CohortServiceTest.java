package com.lms.modules.enrollment;

import com.lms.modules.enrollment.dto.AddCohortMembersRequest;
import com.lms.modules.enrollment.dto.CohortEnrollmentResponse;
import com.lms.modules.enrollment.dto.CohortRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CohortServiceTest {

    @Mock private CohortRepository  cohortRepository;
    @Mock private EnrollmentService enrollmentService;

    private CohortService service;

    private final UUID cohortId = UUID.randomUUID();
    private final UUID pscId    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CohortService(cohortRepository, enrollmentService);
        when(cohortRepository.findById(cohortId)).thenReturn(Optional.of(new Cohort()));
        when(cohortRepository.findMembers(any())).thenReturn(List.of());
    }

    private static CohortMemberView user(UUID id, String number, String role, String status) {
        return new CohortMemberView() {
            public String getStudentId()     { return id.toString(); }
            public String getName()          { return "User " + number; }
            public String getEmail()         { return number + "@example.edu"; }
            public String getStudentNumber() { return number; }
            public String getRole()          { return role; }
            public String getStatus()        { return status; }
        };
    }

    @Test
    @DisplayName("cohort names are unique regardless of case")
    void duplicateNameRefused() {
        when(cohortRepository.existsByNameIgnoreCase("BSCS 2024")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CohortRequest("  BSCS 2024 ", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("pasted roll numbers are matched case-insensitively; unknowns and non-students are reported")
    void addMembersByRollNumber() {
        UUID student = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();
        when(cohortRepository.findUsersByStudentNumbers(anyCollection())).thenReturn(List.of(
                user(student, "BSCS-001", "STUDENT", "ACTIVE"),
                user(teacher, "BSCS-002", "TEACHER", "ACTIVE")));
        when(cohortRepository.addMember(cohortId, student)).thenReturn(1);

        var res = service.addMembers(cohortId, new AddCohortMembersRequest(
                null, List.of("bscs-001", "BSCS-002", "BSCS-999", " ")));

        assertThat(res.added()).isEqualTo(1);
        assertThat(res.notFound()).containsExactly("BSCS-999");
        assertThat(res.notStudents()).containsExactly("User BSCS-002 (TEACHER)");
        verify(cohortRepository, never()).addMember(cohortId, teacher);
    }

    @Test
    @DisplayName("a student given by id and by roll number is added once")
    void addMembersDeduplicates() {
        UUID student = UUID.randomUUID();
        var view = user(student, "BSCS-001", "STUDENT", "ACTIVE");
        when(cohortRepository.findUsersByIds(anyCollection())).thenReturn(List.of(view));
        when(cohortRepository.findUsersByStudentNumbers(anyCollection())).thenReturn(List.of(view));
        when(cohortRepository.addMember(cohortId, student)).thenReturn(0);

        var res = service.addMembers(cohortId, new AddCohortMembersRequest(
                List.of(student), List.of("BSCS-001")));

        assertThat(res.added()).isZero();
        assertThat(res.alreadyMembers()).isEqualTo(1);
    }

    @Test
    @DisplayName("enrolling skips inactive accounts and reports the enrollment service's skips")
    void enrollReportsEveryMember() {
        UUID ok = UUID.randomUUID();
        UUID inactive = UUID.randomUUID();
        UUID alreadyIn = UUID.randomUUID();
        when(cohortRepository.findMembers(cohortId)).thenReturn(List.of(
                user(ok, "001", "STUDENT", "ACTIVE"),
                user(inactive, "002", "STUDENT", "INACTIVE"),
                user(alreadyIn, "003", "STUDENT", "ACTIVE")));
        when(enrollmentService.enrollStudents(eq(pscId), eq(List.of(ok, alreadyIn)))).thenReturn(List.of(
                new EnrollmentService.BulkOutcome(ok, null),
                new EnrollmentService.BulkOutcome(alreadyIn, "Already a member (STUDENT)")));

        var res = service.enroll(cohortId, pscId);

        assertThat(res.enrolled()).isEqualTo(1);
        assertThat(res.skipped()).isEqualTo(2);
        assertThat(res.results()).extracting(CohortEnrollmentResponse.Result::outcome)
                .containsExactly("ENROLLED", "SKIPPED", "SKIPPED");
        assertThat(res.results().get(1).reason()).isEqualTo("Account is inactive");
    }

    @Test
    @DisplayName("an empty cohort cannot be enrolled")
    void emptyCohortRefused() {
        assertThatThrownBy(() -> service.enroll(cohortId, pscId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
