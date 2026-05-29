package com.lms.modules.files;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileUploadRepository extends JpaRepository<FileUpload, UUID> {

    Optional<FileUpload> findByObjectKey(String objectKey);

    List<FileUpload> findAllByContextAndContextId(String context, UUID contextId);

    List<FileUpload> findAllByUploadedByOrderByCreatedAtDesc(UUID uploadedBy);
}
