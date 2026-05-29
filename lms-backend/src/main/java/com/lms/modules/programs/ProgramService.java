package com.lms.modules.programs;

import com.lms.infrastructure.messaging.KafkaEventPublisher;
import com.lms.modules.programs.dto.*;
import com.lms.shared.CacheNames;
import com.lms.shared.events.SemesterEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgramService {

    private final ProgramRepository  programRepository;
    private final PloRepository      ploRepository;
    private final SemesterRepository semesterRepository;
    private final KafkaEventPublisher kafkaEventPublisher;

    // ── Programs ──────────────────────────────────────────────────────────────

    @Transactional
    public ProgramSummaryResponse createProgram(CreateProgramRequest req) {
        if (programRepository.existsByCode(req.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Program code already exists: " + req.code());
        }
        var program = new Program();
        program.setName(req.name());
        program.setCode(req.code());
        program.setDescription(req.description());
        program.setDurationYears(req.durationYears());
        return toSummary(programRepository.save(program));
    }

    public Page<ProgramSummaryResponse> listPrograms(String status, Pageable pageable) {
        if (status != null) {
            return programRepository.findAllByStatus(status, pageable).map(this::toSummary);
        }
        return programRepository.findAll(pageable).map(this::toSummary);
    }

    @Cacheable(value = CacheNames.PROGRAMS, key = "#id")
    public ProgramDetailResponse getProgramDetail(UUID id) {
        var program = requireProgram(id);
        var plos    = ploRepository.findAllByProgramIdOrderByOrderIndex(id)
                .stream().map(this::toPloResponse).toList();
        return toDetail(program, plos);
    }

    @Transactional
    @CacheEvict(value = CacheNames.PROGRAMS, key = "#id")
    public ProgramDetailResponse updateProgram(UUID id, UpdateProgramRequest req) {
        var program = requireProgram(id);
        program.setName(req.name());
        program.setDescription(req.description());
        program.setDurationYears(req.durationYears());
        programRepository.save(program);
        var plos = ploRepository.findAllByProgramIdOrderByOrderIndex(id)
                .stream().map(this::toPloResponse).toList();
        return toDetail(program, plos);
    }

    @Transactional
    @CacheEvict(value = CacheNames.PROGRAMS, key = "#id")
    public ProgramSummaryResponse changeProgramStatus(UUID id, ChangeProgramStatusRequest req) {
        var program = requireProgram(id);
        program.setStatus(req.status());
        return toSummary(programRepository.save(program));
    }

    // ── PLOs ──────────────────────────────────────────────────────────────────

    public List<PloResponse> listPlos(UUID programId) {
        requireProgram(programId);
        return ploRepository.findAllByProgramIdOrderByOrderIndex(programId)
                .stream().map(this::toPloResponse).toList();
    }

    @Transactional
    @CacheEvict(value = CacheNames.PROGRAMS, key = "#programId")
    public PloResponse createPlo(UUID programId, CreatePloRequest req) {
        var program = requireProgram(programId);
        if (ploRepository.existsByProgramIdAndCode(programId, req.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "PLO code already exists in this program: " + req.code());
        }
        if (ploRepository.existsByProgramIdAndOrderIndex(programId, req.orderIndex())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "order_index " + req.orderIndex() + " already used in this program");
        }
        var plo = new Plo();
        plo.setProgram(program);
        plo.setCode(req.code());
        plo.setTitle(req.title());
        plo.setDescription(req.description());
        plo.setOrderIndex(req.orderIndex());
        return toPloResponse(ploRepository.save(plo));
    }

    @Transactional
    @CacheEvict(value = CacheNames.PROGRAMS, key = "#programId")
    public PloResponse updatePlo(UUID programId, UUID ploId, UpdatePloRequest req) {
        var plo = requirePlo(ploId, programId);
        // Check code uniqueness (exclude self)
        ploRepository.findAllByProgramIdOrderByOrderIndex(programId).stream()
                .filter(p -> !p.getId().equals(ploId) && p.getCode().equals(req.code()))
                .findFirst().ifPresent(p -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "PLO code already used: " + req.code());
                });
        // Check order_index uniqueness (exclude self)
        ploRepository.findAllByProgramIdOrderByOrderIndex(programId).stream()
                .filter(p -> !p.getId().equals(ploId) && p.getOrderIndex() == req.orderIndex())
                .findFirst().ifPresent(p -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "order_index " + req.orderIndex() + " already used");
                });
        plo.setCode(req.code());
        plo.setTitle(req.title());
        plo.setDescription(req.description());
        plo.setOrderIndex(req.orderIndex());
        return toPloResponse(ploRepository.save(plo));
    }

    @Transactional
    @CacheEvict(value = CacheNames.PROGRAMS, key = "#programId")
    public void deletePlo(UUID programId, UUID ploId) {
        var plo = requirePlo(ploId, programId);
        ploRepository.delete(plo);
    }

    // ── Semesters ─────────────────────────────────────────────────────────────

    @Transactional
    public SemesterResponse createSemester(UUID programId, CreateSemesterRequest req) {
        var program = requireProgram(programId);
        if (!req.endDate().isAfter(req.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "endDate must be after startDate");
        }
        if (semesterRepository.existsByProgramIdAndName(programId, req.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Semester name already exists in this program: " + req.name());
        }
        var semester = new Semester();
        semester.setProgram(program);
        semester.setName(req.name());
        semester.setStartDate(req.startDate());
        semester.setEndDate(req.endDate());
        return toSemesterResponse(semesterRepository.save(semester));
    }

    public List<SemesterResponse> listSemesters(UUID programId, String status) {
        requireProgram(programId);
        List<Semester> semesters = (status != null)
                ? semesterRepository.findAllByProgramIdAndStatus(programId, status)
                : semesterRepository.findAllByProgramIdOrderByStartDateDesc(programId);
        return semesters.stream().map(this::toSemesterResponse).toList();
    }

    public SemesterResponse getSemester(UUID semesterId) {
        return toSemesterResponse(requireSemester(semesterId));
    }

    @Transactional
    public SemesterResponse updateSemester(UUID semesterId, UpdateSemesterRequest req) {
        var semester = requireSemester(semesterId);
        if (!"OPEN".equals(semester.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only OPEN semesters can be edited");
        }
        if (!req.endDate().isAfter(req.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "endDate must be after startDate");
        }
        semester.setName(req.name());
        semester.setStartDate(req.startDate());
        semester.setEndDate(req.endDate());
        return toSemesterResponse(semesterRepository.save(semester));
    }

    @Transactional
    public SemesterResponse closeSemester(UUID semesterId, UUID actorId, String actorEmail) {
        var semester = requireSemester(semesterId);
        if (!"OPEN".equals(semester.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Semester is already closed");
        }
        semester.setStatus("CLOSED");
        semester.setClosedAt(LocalDateTime.now());
        semester.setClosedById(actorId);
        var saved = semesterRepository.save(semester);

        kafkaEventPublisher.publishSemesterEvent(SemesterEvent.builder()
                .action(SemesterEvent.Action.CLOSED)
                .semesterId(semesterId)
                .semesterName(semester.getName())
                .programId(semester.getProgram().getId())
                .programName(semester.getProgram().getName())
                .triggeredByEmail(actorEmail)
                .build());

        return toSemesterResponse(saved);
    }

    @Transactional
    public SemesterResponse reopenSemester(UUID semesterId, String actorEmail) {
        var semester = requireSemester(semesterId);
        if (!"CLOSED".equals(semester.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Semester is not closed");
        }
        UUID programId = semester.getProgram().getId();
        if (semesterRepository.existsByProgramIdAndStatus(programId, "OPEN")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Program already has an open semester");
        }
        semester.setStatus("OPEN");
        semester.setClosedAt(null);
        semester.setClosedById(null);
        var saved = semesterRepository.save(semester);

        kafkaEventPublisher.publishSemesterEvent(SemesterEvent.builder()
                .action(SemesterEvent.Action.REOPENED)
                .semesterId(semesterId)
                .semesterName(semester.getName())
                .programId(programId)
                .programName(semester.getProgram().getName())
                .triggeredByEmail(actorEmail)
                .build());

        return toSemesterResponse(saved);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Program requireProgram(UUID id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Program not found: " + id));
    }

    private Plo requirePlo(UUID ploId, UUID programId) {
        var plo = ploRepository.findById(ploId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "PLO not found: " + ploId));
        if (!plo.getProgram().getId().equals(programId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "PLO " + ploId + " does not belong to program " + programId);
        }
        return plo;
    }

    private Semester requireSemester(UUID id) {
        return semesterRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Semester not found: " + id));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private ProgramSummaryResponse toSummary(Program p) {
        return new ProgramSummaryResponse(p.getId(), p.getName(), p.getCode(),
                p.getDescription(), p.getDurationYears(), p.getStatus(), p.getCreatedAt());
    }

    private ProgramDetailResponse toDetail(Program p, List<PloResponse> plos) {
        return new ProgramDetailResponse(p.getId(), p.getName(), p.getCode(),
                p.getDescription(), p.getDurationYears(), p.getStatus(), p.getCreatedAt(), plos);
    }

    PloResponse toPloResponse(Plo p) {
        return new PloResponse(p.getId(), p.getProgram().getId(), p.getCode(),
                p.getTitle(), p.getDescription(), p.getOrderIndex(), p.getCreatedAt());
    }

    SemesterResponse toSemesterResponse(Semester s) {
        return new SemesterResponse(s.getId(), s.getProgram().getId(),
                s.getProgram().getName(), s.getName(), s.getStartDate(), s.getEndDate(),
                s.getStatus(), s.getClosedAt(), s.getClosedById(), s.getCreatedAt());
    }
}
