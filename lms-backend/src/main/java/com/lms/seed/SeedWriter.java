package com.lms.seed;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Buffers rows per table and writes them with JDBC batch inserts.
 *
 * <p>Tables are declared up front with {@link #table}, parents before
 * children, and {@link #flush} writes them in that order, so foreign keys
 * always point at rows already inserted. A column written as
 * {@code "content::jsonb"} is bound with that cast, for JSONB columns filled
 * from a JSON string.
 */
final class SeedWriter {

    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbc;
    private final Map<String, Table> tables = new LinkedHashMap<>();

    SeedWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Table table(String name, String... columns) {
        Table table = new Table(name, columns);
        tables.put(name, table);
        return table;
    }

    /** Writes every buffered row, table by table in declaration order. */
    void flush() {
        for (Table table : tables.values()) {
            table.flush();
        }
    }

    /** Rows written so far, per table, in declaration order. */
    Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        tables.forEach((name, table) -> counts.put(name, table.written));
        return counts;
    }

    final class Table {

        private final String sql;
        private final int width;
        private final List<Object[]> rows = new ArrayList<>();
        private long written;

        private Table(String name, String[] columns) {
            this.width = columns.length;
            String names = Arrays.stream(columns)
                    .map(c -> c.contains("::") ? c.substring(0, c.indexOf("::")) : c)
                    .collect(Collectors.joining(", "));
            String values = Arrays.stream(columns)
                    .map(c -> c.contains("::") ? "?" + c.substring(c.indexOf("::")) : "?")
                    .collect(Collectors.joining(", "));
            this.sql = "INSERT INTO " + name + " (" + names + ") VALUES (" + values + ")";
        }

        void add(Object... values) {
            if (values.length != width) {
                throw new IllegalArgumentException(sql + ": expected " + width + " values, got " + values.length);
            }
            rows.add(values);
        }

        private void flush() {
            for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
                jdbc.batchUpdate(sql, rows.subList(from, Math.min(from + BATCH_SIZE, rows.size())));
            }
            written += rows.size();
            rows.clear();
        }
    }
}
