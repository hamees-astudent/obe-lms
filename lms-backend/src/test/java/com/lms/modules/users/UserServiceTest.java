package com.lms.modules.users;

import com.lms.modules.users.dto.ChangeRoleRequest;
import com.lms.modules.users.dto.ChangeStatusRequest;
import com.lms.shared.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An admin must not be able to demote or deactivate their own account: they
 * lose access mid-session, and if they were the only admin nobody can undo it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock private UserRepository           userRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private TeacherProfileRepository teacherProfileRepository;
    @Mock private PasswordEncoder          passwordEncoder;

    private UserService service;

    private final UUID adminId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, studentProfileRepository,
                teacherProfileRepository, passwordEncoder);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(user(adminId)));
        when(userRepository.findById(otherId)).thenReturn(Optional.of(user(otherId)));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("an admin cannot change their own role")
    void cannotChangeOwnRole() {
        assertForbidden(() -> service.changeRole(adminId, new ChangeRoleRequest(Role.TEACHER), adminId));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("an admin cannot deactivate their own account")
    void cannotDeactivateSelf() {
        assertForbidden(() -> service.changeStatus(adminId, new ChangeStatusRequest("INACTIVE"), adminId));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("an admin cannot suspend their own account")
    void cannotSuspendSelf() {
        assertForbidden(() -> service.changeStatus(adminId, new ChangeStatusRequest("SUSPENDED"), adminId));
    }

    @Test
    @DisplayName("re-saving your own unchanged role or ACTIVE status is harmless and allowed")
    void noOpOnSelfIsAllowed() {
        assertThat(service.changeRole(adminId, new ChangeRoleRequest(Role.ADMIN), adminId).role())
                .isEqualTo(Role.ADMIN);
        assertThat(service.changeStatus(adminId, new ChangeStatusRequest("ACTIVE"), adminId).status())
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("an admin can still change another user's role and status")
    void canChangeOthers() {
        assertThat(service.changeRole(otherId, new ChangeRoleRequest(Role.TEACHER), adminId).role())
                .isEqualTo(Role.TEACHER);
        assertThat(service.changeStatus(otherId, new ChangeStatusRequest("INACTIVE"), adminId).status())
                .isEqualTo("INACTIVE");
    }

    private static void assertForbidden(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static User user(UUID id) {
        var u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setName("Admin");
        u.setEmail(id + "@lms.local");
        u.setRole(Role.ADMIN);
        u.setStatus("ACTIVE");
        return u;
    }
}
