package com.lms.modules.transcript;

import com.lms.modules.transcript.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GradingScaleController {

    private final GradingScaleService gradingScaleService;

    // ── Grading Scales ───────────────────────────────────────────────────────

    /** Create a new grading scale. ADMIN only. */
    @PostMapping("/api/admin/grading-scales")
    @ResponseStatus(HttpStatus.CREATED)
    public GradingScaleResponse createScale(@Valid @RequestBody CreateGradingScaleRequest req) {
        return gradingScaleService.createScale(req);
    }

    /**
     * List grading scales.
     * If {@code programId} is provided, returns program-specific scales.
     * Otherwise returns global (program-agnostic) scales.
     */
    @GetMapping("/api/admin/grading-scales")
    public List<GradingScaleResponse> listScales(
            @RequestParam(required = false) UUID programId) {
        if (programId != null) {
            return gradingScaleService.listScalesForProgram(programId);
        }
        return gradingScaleService.listGlobalScales();
    }

    /** Get a single grading scale with its entries. ADMIN only. */
    @GetMapping("/api/admin/grading-scales/{id}")
    public GradingScaleResponse getScale(@PathVariable UUID id) {
        return gradingScaleService.getScale(id);
    }

    /** Update a grading scale. ADMIN only. */
    @PutMapping("/api/admin/grading-scales/{id}")
    public GradingScaleResponse updateScale(
            @PathVariable UUID id,
            @Valid @RequestBody CreateGradingScaleRequest req) {
        return gradingScaleService.updateScale(id, req);
    }

    /** Delete a grading scale (also removes all its entries). ADMIN only. */
    @DeleteMapping("/api/admin/grading-scales/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScale(@PathVariable UUID id) {
        gradingScaleService.deleteScale(id);
    }

    // ── Scale Entries ────────────────────────────────────────────────────────

    /** Add a grade entry to a scale. ADMIN only. */
    @PostMapping("/api/admin/grading-scales/{id}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public GradingScaleEntryResponse addEntry(
            @PathVariable UUID id,
            @Valid @RequestBody AddScaleEntryRequest req) {
        return gradingScaleService.addEntry(id, req);
    }

    /** Remove a specific entry from a scale. ADMIN only. */
    @DeleteMapping("/api/admin/grading-scales/{id}/entries/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeEntry(@PathVariable UUID id, @PathVariable UUID entryId) {
        gradingScaleService.removeEntry(id, entryId);
    }
}
