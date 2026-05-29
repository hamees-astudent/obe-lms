package com.lms.modules.transcript.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class CreateGradingScaleRequest {

    /** If null the scale is global (applies to all programs unless overridden). */
    private UUID programId;

    @NotBlank
    private String name;

    private boolean isDefault = false;

    // ── Getters / Setters ────────────────────────────────────────────────────

    public UUID getProgramId() { return programId; }
    public void setProgramId(UUID programId) { this.programId = programId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
}
