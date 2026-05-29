package com.lms.modules.transcript;

import com.lms.infrastructure.cache.CacheNames;
import com.lms.modules.transcript.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class GradingScaleService {

    private final GradingScaleRepository      scaleRepo;
    private final GradingScaleEntryRepository entryRepo;

    // ── Create / update ──────────────────────────────────────────────────────

    @CacheEvict(value = CacheNames.GRADING_SCALES, allEntries = true)
    public GradingScaleResponse createScale(CreateGradingScaleRequest req) {
        GradingScale scale = new GradingScale();
        scale.setProgramId(req.getProgramId());
        scale.setName(req.getName());
        scale.setDefault(req.isDefault());
        return toResponse(scaleRepo.save(scale), List.of());
    }

    @CacheEvict(value = CacheNames.GRADING_SCALES, allEntries = true)
    public GradingScaleResponse updateScale(UUID id, CreateGradingScaleRequest req) {
        GradingScale scale = findOrThrow(id);
        scale.setProgramId(req.getProgramId());
        scale.setName(req.getName());
        scale.setDefault(req.isDefault());
        List<GradingScaleEntry> entries = entryRepo.findByScaleIdOrderByOrderIndexAsc(id);
        return toResponse(scaleRepo.save(scale), entries);
    }

    @CacheEvict(value = CacheNames.GRADING_SCALES, allEntries = true)
    public void deleteScale(UUID id) {
        findOrThrow(id);
        entryRepo.deleteAllByScaleId(id);
        scaleRepo.deleteById(id);
    }

    // ── Entries ──────────────────────────────────────────────────────────────

    @CacheEvict(value = CacheNames.GRADING_SCALES, allEntries = true)
    public GradingScaleEntryResponse addEntry(UUID scaleId, AddScaleEntryRequest req) {
        findOrThrow(scaleId);
        GradingScaleEntry entry = new GradingScaleEntry();
        entry.setScaleId(scaleId);
        entry.setGradeLetter(req.getGradeLetter());
        entry.setMinPercentage(req.getMinPercentage());
        entry.setMaxPercentage(req.getMaxPercentage());
        entry.setGradePoints(req.getGradePoints());
        entry.setOrderIndex(req.getOrderIndex());
        return toEntryResponse(entryRepo.save(entry));
    }

    @CacheEvict(value = CacheNames.GRADING_SCALES, allEntries = true)
    public void removeEntry(UUID scaleId, UUID entryId) {
        findOrThrow(scaleId);
        GradingScaleEntry entry = entryRepo.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scale entry not found"));
        if (!entry.getScaleId().equals(scaleId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entry does not belong to this scale");
        }
        entryRepo.deleteById(entryId);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Cacheable(CacheNames.GRADING_SCALES)
    public List<GradingScaleResponse> listGlobalScales() {
        return scaleRepo.findAllByProgramIdIsNullOrderByCreatedAtDesc()
                .stream()
                .map(s -> toResponse(s, entryRepo.findByScaleIdOrderByOrderIndexAsc(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GradingScaleResponse> listScalesForProgram(UUID programId) {
        return scaleRepo.findAllByProgramIdOrderByCreatedAtDesc(programId)
                .stream()
                .map(s -> toResponse(s, entryRepo.findByScaleIdOrderByOrderIndexAsc(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public GradingScaleResponse getScale(UUID id) {
        GradingScale scale = findOrThrow(id);
        List<GradingScaleEntry> entries = entryRepo.findByScaleIdOrderByOrderIndexAsc(id);
        return toResponse(scale, entries);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private GradingScale findOrThrow(UUID id) {
        return scaleRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grading scale not found"));
    }

    GradingScaleResponse toResponse(GradingScale scale, List<GradingScaleEntry> entries) {
        return GradingScaleResponse.builder()
                .id(scale.getId())
                .programId(scale.getProgramId())
                .name(scale.getName())
                .isDefault(scale.isDefault())
                .entries(entries.stream().map(this::toEntryResponse).toList())
                .createdAt(scale.getCreatedAt())
                .updatedAt(scale.getUpdatedAt())
                .build();
    }

    GradingScaleEntryResponse toEntryResponse(GradingScaleEntry e) {
        return GradingScaleEntryResponse.builder()
                .id(e.getId())
                .scaleId(e.getScaleId())
                .gradeLetter(e.getGradeLetter())
                .minPercentage(e.getMinPercentage())
                .maxPercentage(e.getMaxPercentage())
                .gradePoints(e.getGradePoints())
                .orderIndex(e.getOrderIndex())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
