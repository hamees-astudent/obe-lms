package com.lms.modules.studyai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.modules.studyai.dto.PaperSettingsRequest;
import com.lms.modules.studyai.dto.PaperSettingsResponse;
import com.lms.shared.OfferingStaff;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * An offering's limits on generated papers — the most marks, questions and
 * minutes one may have, and the smaller cap on students' mock papers — and
 * the marks table questions are priced with. An offering without a row uses
 * the defaults.
 */
@Service
@RequiredArgsConstructor
public class PaperSettingsService {

    static final BigDecimal DEFAULT_MAX_MARKS = BigDecimal.valueOf(100);
    static final int DEFAULT_MAX_QUESTIONS = 40;
    static final int DEFAULT_MAX_DURATION = 180;
    static final BigDecimal DEFAULT_MOCK_MAX_MARKS = BigDecimal.valueOf(50);

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final OfferingStaff offeringStaff;

    public record Settings(BigDecimal maxTotalMarks, int maxQuestions, int maxDurationMinutes,
                           BigDecimal mockMaxTotalMarks, MarksPolicy marks) {}

    public Settings settings(UUID pscId) {
        return jdbc.query("""
                SELECT max_total_marks, max_questions, max_duration_minutes, mock_max_total_marks,
                       marks_table::text AS marks_table
                FROM   offering_paper_settings WHERE psc_id = :psc
                """, Map.of("psc", pscId),
                (rs, i) -> new Settings(rs.getBigDecimal("max_total_marks"), rs.getInt("max_questions"),
                        rs.getInt("max_duration_minutes"), rs.getBigDecimal("mock_max_total_marks"),
                        Sql.read(mapper, rs.getString("marks_table"), MarksPolicy.class, MarksPolicy.defaults())
                                .withDefaults()))
                .stream().findFirst()
                .orElse(new Settings(DEFAULT_MAX_MARKS, DEFAULT_MAX_QUESTIONS, DEFAULT_MAX_DURATION,
                        DEFAULT_MOCK_MAX_MARKS, MarksPolicy.defaults()));
    }

    public PaperSettingsResponse get(UUID pscId, UUID actorId, boolean isAdmin) {
        offeringStaff.require(pscId, actorId, isAdmin);
        return toResponse(pscId, settings(pscId));
    }

    public PaperSettingsResponse update(UUID pscId, PaperSettingsRequest req, UUID actorId, boolean isAdmin) {
        offeringStaff.require(pscId, actorId, isAdmin);
        MarksPolicy table = req.marksTable() == null ? null : req.marksTable().withDefaults();
        jdbc.update("""
                INSERT INTO offering_paper_settings (psc_id, max_total_marks, max_questions, max_duration_minutes,
                                                     mock_max_total_marks, marks_table, updated_by)
                VALUES (:psc, :m, :q, :d, :mm, CAST(:t AS jsonb), :by)
                ON CONFLICT (psc_id) DO UPDATE SET
                    max_total_marks = :m, max_questions = :q, max_duration_minutes = :d,
                    mock_max_total_marks = :mm, marks_table = CAST(:t AS jsonb), updated_by = :by
                """, new MapSqlParameterSource("psc", pscId).addValue("m", req.maxTotalMarks())
                        .addValue("q", req.maxQuestions()).addValue("d", req.maxDurationMinutes())
                        .addValue("mm", req.mockMaxTotalMarks())
                        .addValue("t", table == null ? null : Sql.json(mapper, table))
                        .addValue("by", actorId));
        return toResponse(pscId, settings(pscId));
    }

    private static PaperSettingsResponse toResponse(UUID pscId, Settings s) {
        return new PaperSettingsResponse(pscId, s.maxTotalMarks(), s.maxQuestions(), s.maxDurationMinutes(),
                s.mockMaxTotalMarks(), s.marks());
    }
}
