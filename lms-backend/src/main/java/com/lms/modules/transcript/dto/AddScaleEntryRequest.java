package com.lms.modules.transcript.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class AddScaleEntryRequest {

    @NotBlank
    @Size(max = 3)
    private String gradeLetter;

    @NotNull
    private BigDecimal minPercentage;

    @NotNull
    private BigDecimal maxPercentage;

    @NotNull
    private BigDecimal gradePoints;

    @Min(1)
    private int orderIndex;

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getGradeLetter() { return gradeLetter; }
    public void setGradeLetter(String gradeLetter) { this.gradeLetter = gradeLetter; }

    public BigDecimal getMinPercentage() { return minPercentage; }
    public void setMinPercentage(BigDecimal minPercentage) { this.minPercentage = minPercentage; }

    public BigDecimal getMaxPercentage() { return maxPercentage; }
    public void setMaxPercentage(BigDecimal maxPercentage) { this.maxPercentage = maxPercentage; }

    public BigDecimal getGradePoints() { return gradePoints; }
    public void setGradePoints(BigDecimal gradePoints) { this.gradePoints = gradePoints; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}
