package com.lms.modules.studyai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * JSON and array conversions for the module's plain-SQL repositories.
 *
 * <p>Arrays travel as PostgreSQL array literals cast in SQL
 * ({@code CAST(:ids AS uuid[])}), and JSON as text cast to {@code jsonb},
 * which keeps the repositories free of driver-specific types.
 */
final class Sql {

    private Sql() {}

    static String uuidArray(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return "{}";
        StringJoiner j = new StringJoiner(",", "{", "}");
        ids.forEach(id -> j.add(id.toString()));
        return j.toString();
    }

    static String textArray(Collection<String> values) {
        if (values == null || values.isEmpty()) return "{}";
        StringJoiner j = new StringJoiner(",", "{", "}");
        for (String v : values) {
            j.add('"' + v.replace("\\", "\\\\").replace("\"", "\\\"") + '"');
        }
        return j.toString();
    }

    static List<UUID> uuids(ResultSet rs, String column) throws SQLException {
        Array a = rs.getArray(column);
        if (a == null) return List.of();
        Object[] values = (Object[]) a.getArray();
        List<UUID> out = new ArrayList<>(values.length);
        for (Object v : values) out.add(v instanceof UUID u ? u : UUID.fromString(v.toString()));
        return out;
    }

    static List<String> texts(ResultSet rs, String column) throws SQLException {
        Array a = rs.getArray(column);
        if (a == null) return List.of();
        Object[] values = (Object[]) a.getArray();
        List<String> out = new ArrayList<>(values.length);
        for (Object v : values) out.add(String.valueOf(v));
        return out;
    }

    static String json(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot serialise to JSON", e);
        }
    }

    static <T> T read(ObjectMapper mapper, String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            return fallback;
        }
    }

    static <T> T read(ObjectMapper mapper, String json, Class<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            return fallback;
        }
    }

    static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {};
    static final TypeReference<Map<String, List<String>>> ANSWERS = new TypeReference<>() {};
}
