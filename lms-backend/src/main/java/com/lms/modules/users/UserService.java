package com.lms.modules.users;

import com.lms.modules.users.dto.*;
import com.lms.shared.CacheNames;
import com.lms.shared.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository            userRepository;
    private final StudentProfileRepository  studentProfileRepository;
    private final TeacherProfileRepository  teacherProfileRepository;
    private final PasswordEncoder           passwordEncoder;

    // ── Admin: CRUD ───────────────────────────────────────────────────────────

    @Transactional
    public UserSummaryResponse createUser(CreateUserRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already in use: " + req.email());
        }
        var user = new User();
        user.setName(req.name());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(req.role());
        return toSummary(userRepository.save(user));
    }

    public Page<UserSummaryResponse> listUsers(Role role, String status, Pageable pageable) {
        Page<User> page;
        if (role != null && status != null) {
            page = userRepository.findAllByRoleAndStatus(role, status, pageable);
        } else if (role != null) {
            page = userRepository.findAllByRole(role, pageable);
        } else if (status != null) {
            page = userRepository.findAllByStatus(status, pageable);
        } else {
            page = userRepository.findAll(pageable);
        }
        return page.map(this::toSummary);
    }

    @Cacheable(value = CacheNames.USERS, key = "#id")
    public UserDetailResponse getUserDetail(UUID id) {
        return toDetail(requireUser(id));
    }

    @Transactional
    @CacheEvict(value = CacheNames.USERS, key = "#id")
    public UserDetailResponse updateUser(UUID id, UpdateUserRequest req) {
        var user = requireUser(id);
        if (!user.getEmail().equals(req.email()) && userRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already in use: " + req.email());
        }
        user.setName(req.name());
        user.setEmail(req.email());
        return toDetail(userRepository.save(user));
    }

    @Transactional
    @CacheEvict(value = CacheNames.USERS, key = "#id")
    public UserSummaryResponse changeRole(UUID id, ChangeRoleRequest req) {
        var user = requireUser(id);
        user.setRole(req.role());
        return toSummary(userRepository.save(user));
    }

    @Transactional
    @CacheEvict(value = CacheNames.USERS, key = "#id")
    public UserSummaryResponse changeStatus(UUID id, ChangeStatusRequest req) {
        var user = requireUser(id);
        user.setStatus(req.status());
        return toSummary(userRepository.save(user));
    }

    // ── Admin: profile management ─────────────────────────────────────────────

    public StudentProfileResponse getStudentProfile(UUID userId) {
        return studentProfileRepository.findById(userId)
                .map(this::toStudentProfileResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Student profile not found for user " + userId));
    }

    public TeacherProfileResponse getTeacherProfile(UUID userId) {
        return teacherProfileRepository.findById(userId)
                .map(this::toTeacherProfileResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Teacher profile not found for user " + userId));
    }

    @Transactional
    @CacheEvict(value = CacheNames.USERS, key = "#userId")
    public StudentProfileResponse upsertStudentProfile(UUID userId, StudentProfileRequest req) {
        var user = requireUser(userId);
        if (user.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Student profile is only valid for STUDENT users");
        }
        if (req.studentNumber() != null
                && studentProfileRepository.existsByStudentNumber(req.studentNumber())) {
            var existing = studentProfileRepository.findByStudentNumber(req.studentNumber());
            if (existing.isPresent() && !existing.get().getUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Student number already assigned");
            }
        }
        var profile = studentProfileRepository.findById(userId)
                .orElseGet(() -> {
                    var p = new StudentProfile();
                    p.setUser(user);
                    return p;
                });
        if (req.studentNumber()    != null) profile.setStudentNumber(req.studentNumber());
        if (req.dateOfBirth()      != null) profile.setDateOfBirth(req.dateOfBirth());
        if (req.phone()            != null) profile.setPhone(req.phone());
        if (req.address()          != null) profile.setAddress(req.address());
        if (req.enrollmentDate()   != null) profile.setEnrollmentDate(req.enrollmentDate());
        if (req.profilePictureKey() != null) profile.setProfilePictureKey(req.profilePictureKey());
        return toStudentProfileResponse(studentProfileRepository.save(profile));
    }

    @Transactional
    @CacheEvict(value = CacheNames.USERS, key = "#userId")
    public TeacherProfileResponse upsertTeacherProfile(UUID userId, TeacherProfileRequest req) {
        var user = requireUser(userId);
        if (user.getRole() != Role.TEACHER && user.getRole() != Role.ASSISTANT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Teacher profile is only valid for TEACHER / ASSISTANT users");
        }
        if (req.employeeNumber() != null
                && teacherProfileRepository.existsByEmployeeNumber(req.employeeNumber())) {
            var existing = teacherProfileRepository.findByEmployeeNumber(req.employeeNumber());
            if (existing.isPresent() && !existing.get().getUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Employee number already assigned");
            }
        }
        var profile = teacherProfileRepository.findById(userId)
                .orElseGet(() -> {
                    var p = new TeacherProfile();
                    p.setUser(user);
                    return p;
                });
        if (req.employeeNumber() != null)   profile.setEmployeeNumber(req.employeeNumber());
        if (req.department()     != null)   profile.setDepartment(req.department());
        if (req.designation()    != null)   profile.setDesignation(req.designation());
        if (req.phone()          != null)   profile.setPhone(req.phone());
        if (req.joiningDate()    != null)   profile.setJoiningDate(req.joiningDate());
        if (req.bio()            != null)   profile.setBio(req.bio());
        if (req.profilePictureKey() != null) profile.setProfilePictureKey(req.profilePictureKey());
        return toTeacherProfileResponse(teacherProfileRepository.save(profile));
    }

    // ── Self-service ──────────────────────────────────────────────────────────

    @Cacheable(value = CacheNames.USERS, key = "#currentUserId")
    public UserDetailResponse getMe(UUID currentUserId) {
        return toDetail(requireUser(currentUserId));
    }

    @Transactional
    @CacheEvict(value = CacheNames.USERS, key = "#currentUserId")
    public UserDetailResponse updateMyName(UUID currentUserId, String name) {
        var user = requireUser(currentUserId);
        user.setName(name);
        return toDetail(userRepository.save(user));
    }

    @Transactional
    @CacheEvict(value = CacheNames.USERS, key = "#currentUserId")
    public void changeOwnPassword(UUID currentUserId, ChangePasswordRequest req) {
        var user = requireUser(currentUserId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found: " + id));
    }

    private UserSummaryResponse toSummary(User u) {
        return new UserSummaryResponse(u.getId(), u.getName(), u.getEmail(),
                u.getRole(), u.getStatus(), u.getCreatedAt());
    }

    private UserDetailResponse toDetail(User u) {
        var sp = studentProfileRepository.findById(u.getId())
                .map(this::toStudentProfileResponse).orElse(null);
        var tp = teacherProfileRepository.findById(u.getId())
                .map(this::toTeacherProfileResponse).orElse(null);
        return new UserDetailResponse(u.getId(), u.getName(), u.getEmail(),
                u.getRole(), u.getStatus(), u.getCreatedAt(), sp, tp);
    }

    private StudentProfileResponse toStudentProfileResponse(StudentProfile p) {
        return new StudentProfileResponse(p.getUserId(), p.getStudentNumber(),
                p.getDateOfBirth(), p.getPhone(), p.getAddress(),
                p.getProfilePictureKey(), p.getEnrollmentDate(), p.getCreatedAt());
    }

    private TeacherProfileResponse toTeacherProfileResponse(TeacherProfile p) {
        return new TeacherProfileResponse(p.getUserId(), p.getEmployeeNumber(),
                p.getDepartment(), p.getDesignation(), p.getPhone(),
                p.getProfilePictureKey(), p.getJoiningDate(), p.getBio(), p.getCreatedAt());
    }
}
