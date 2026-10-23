package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.LlmGateway;
import com.lms.infrastructure.nlp.NlpProperties;
import com.lms.infrastructure.nlp.TextAnalyzer;
import com.lms.modules.studyai.dto.AiStatusResponse;
import com.lms.modules.studyai.dto.IngestionStatusResponse;
import com.lms.modules.studyai.dto.TopicResponse;
import com.lms.shared.OfferingStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** What has been indexed for an offering, its topics, and the NLP engine's state. */
@Service
@RequiredArgsConstructor
public class MaterialIndexService {

    private final IngestionRepository repository;
    private final IngestionService ingestion;
    private final StudentScope scope;
    private final OfferingStaff offeringStaff;
    private final LlmGateway llm;
    private final TextAnalyzer analyzer;
    private final NlpProperties properties;

    public AiStatusResponse status() {
        boolean up = llm.available();
        return new AiStatusResponse(properties.getLlm().isEnabled(), up, llm.modelName(), up ? "LLM" : "BASIC",
                analyzer.modelsLoaded());
    }

    public List<IngestionStatusResponse> ingestionStatus(UUID pscId, UUID actorId, boolean isAdmin) {
        offeringStaff.require(pscId, actorId, isAdmin);
        return repository.statusForOffering(pscId);
    }

    /** Re-reads one material, or every unindexed document of the offering when materialId is null. */
    public void reindex(UUID pscId, UUID materialId, UUID actorId, boolean isAdmin) {
        offeringStaff.require(pscId, actorId, isAdmin);
        if (materialId == null) {
            repository.unindexedDocuments(pscId).forEach(id -> {
                repository.markPending(id);
                ingestion.schedule(id);
            });
            return;
        }
        var m = repository.findMaterial(materialId)
                .filter(x -> x.pscId().equals(pscId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));
        if (!"DOCUMENT".equals(m.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only uploaded documents are indexed");
        }
        repository.markPending(materialId);
        ingestion.schedule(materialId);
    }

    /** Staff see every topic; an enrolled student sees topics of published material only. */
    public List<TopicResponse> topics(UUID pscId, UUID userId, boolean isAdmin) {
        if (isAdmin || offeringStaff.isStaff(pscId, userId)) return repository.topics(pscId, false);
        scope.require(pscId, userId);
        return repository.topics(pscId, true);
    }
}
