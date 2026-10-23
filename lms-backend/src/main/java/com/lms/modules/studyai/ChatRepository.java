package com.lms.modules.studyai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.modules.studyai.dto.ChatMessageResponse;
import com.lms.modules.studyai.dto.ChatSessionResponse;
import com.lms.modules.studyai.dto.CitationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class ChatRepository {

    private static final TypeReference<List<CitationResponse>> CITATIONS = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    record SessionRow(UUID id, UUID studentId, UUID pscId, String title) {}

    UUID createSession(UUID studentId, UUID pscId) {
        return jdbc.queryForObject("""
                INSERT INTO chat_sessions (student_id, psc_id) VALUES (:s, :p) RETURNING id
                """, new MapSqlParameterSource("s", studentId).addValue("p", pscId), UUID.class);
    }

    Optional<SessionRow> findSession(UUID id) {
        return jdbc.query("SELECT id, student_id, psc_id, title FROM chat_sessions WHERE id = :id",
                Map.of("id", id), (rs, i) -> new SessionRow(rs.getObject("id", UUID.class),
                        rs.getObject("student_id", UUID.class), rs.getObject("psc_id", UUID.class),
                        rs.getString("title"))).stream().findFirst();
    }

    List<ChatSessionResponse> listSessions(UUID studentId, UUID pscId) {
        return jdbc.query("""
                SELECT id, psc_id, title, created_at, COALESCE(updated_at, created_at) AS updated_at
                FROM   chat_sessions
                WHERE  student_id = :s AND (CAST(:p AS uuid) IS NULL OR psc_id = CAST(:p AS uuid))
                ORDER  BY COALESCE(updated_at, created_at) DESC LIMIT 50
                """, new MapSqlParameterSource("s", studentId).addValue("p", pscId == null ? null : pscId.toString()),
                (rs, i) -> new ChatSessionResponse(rs.getObject("id", UUID.class), rs.getObject("psc_id", UUID.class),
                        rs.getString("title"), rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("updated_at", LocalDateTime.class), null));
    }

    ChatSessionResponse session(UUID id) {
        List<ChatMessageResponse> messages = messages(id, 500);
        return jdbc.query("""
                SELECT id, psc_id, title, created_at, COALESCE(updated_at, created_at) AS updated_at
                FROM   chat_sessions WHERE id = :id
                """, Map.of("id", id),
                (rs, i) -> new ChatSessionResponse(rs.getObject("id", UUID.class), rs.getObject("psc_id", UUID.class),
                        rs.getString("title"), rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("updated_at", LocalDateTime.class), messages)).get(0);
    }

    /** The last {@code limit} messages, oldest first. */
    List<ChatMessageResponse> messages(UUID sessionId, int limit) {
        List<ChatMessageResponse> newestFirst = jdbc.query("""
                SELECT id, role, content, citations, refused, mode, unverified, created_at
                FROM   chat_messages WHERE session_id = :s
                ORDER  BY created_at DESC, role ASC LIMIT :limit
                """, new MapSqlParameterSource("s", sessionId).addValue("limit", limit),
                (rs, i) -> new ChatMessageResponse(rs.getObject("id", UUID.class), rs.getString("role"),
                        rs.getString("content"), Sql.read(mapper, rs.getString("citations"), CITATIONS, List.of()),
                        rs.getBoolean("refused"), rs.getString("mode"), rs.getBoolean("unverified"),
                        rs.getObject("created_at", LocalDateTime.class)));
        return newestFirst.reversed();
    }

    UUID addMessage(UUID sessionId, String role, String content, List<CitationResponse> citations,
                    boolean refused, String mode, boolean unverified) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO chat_messages (session_id, role, content, citations, refused, mode, unverified)
                VALUES (:s, :r, :c, CAST(:cit AS jsonb), :ref, :m, :u) RETURNING id
                """, new MapSqlParameterSource("s", sessionId).addValue("r", role).addValue("c", content)
                        .addValue("cit", Sql.json(mapper, citations == null ? List.of() : citations))
                        .addValue("ref", refused).addValue("m", mode).addValue("u", unverified), UUID.class);
        jdbc.update("UPDATE chat_sessions SET updated_at = now() WHERE id = :s", Map.of("s", sessionId));
        return id;
    }

    void retitle(UUID sessionId, String title) {
        jdbc.update("UPDATE chat_sessions SET title = :t WHERE id = :s",
                new MapSqlParameterSource("s", sessionId).addValue("t", title));
    }

    void deleteSession(UUID sessionId) {
        jdbc.update("DELETE FROM chat_sessions WHERE id = :s", Map.of("s", sessionId));
    }
}
