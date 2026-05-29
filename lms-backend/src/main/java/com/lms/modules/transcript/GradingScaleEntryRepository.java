package com.lms.modules.transcript;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GradingScaleEntryRepository extends JpaRepository<GradingScaleEntry, UUID> {

    List<GradingScaleEntry> findByScaleIdOrderByOrderIndexAsc(UUID scaleId);

    void deleteAllByScaleId(UUID scaleId);
}
