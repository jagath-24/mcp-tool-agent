package com.jagath.mcpserver.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Exposes read-only SQL access to MySQL as an MCP tool.
 * Safety: only SELECT statements are allowed, row count is capped, and the
 * datasource itself should point at a MySQL user with SELECT-only grants
 * (see db/init.sql) — the app-level check below is a second layer, not a
 * substitute for DB-level permissions.
 */
@Component
public class DatabaseTool {

    private final JdbcTemplate jdbcTemplate;

    private static final int MAX_ROWS = 100;

    public DatabaseTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(name = "query_database",
          description = "Runs a read-only SELECT query against the application MySQL "
                  + "database and returns matching rows. Only SELECT statements are "
                  + "permitted — no INSERT/UPDATE/DELETE/DDL. Always include a LIMIT "
                  + "clause for large tables.")
    public String queryDatabase(
            @ToolParam(description = "A single valid MySQL SELECT statement") String sql) {

        // Normalise whitespace and strip a single trailing semicolon (LLMs always add one).
        // Mid-statement semicolons (statement chaining) are still blocked below.
        String normalized = sql.trim().replaceAll("\\s+", " ");
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        String lower = normalized.toLowerCase();

        if (!lower.startsWith("select")) {
            return "Rejected: only SELECT statements are allowed through this tool.";
        }
        if (containsBlockedKeyword(lower)) {
            return "Rejected: query contains a write/DDL keyword, which is not permitted.";
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(normalized);
            if (rows.size() > MAX_ROWS) {
                rows = rows.subList(0, MAX_ROWS);
            }
            if (rows.isEmpty()) {
                return "Query executed successfully, no rows returned.";
            }
            return rows.toString();
        } catch (Exception e) {
            return "Query failed: " + e.getMessage();
        }
    }

    private boolean containsBlockedKeyword(String lowerSql) {
        String[] blocked = {"insert", "update", "delete", "drop", "alter", "create",
                "truncate", "grant", "revoke", "replace", "--", ";"};
        for (String kw : blocked) {
            if (lowerSql.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
