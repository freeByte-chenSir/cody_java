package com.cody.core.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * SQLite-backed audit log for tool executions.
 * Records every tool call with session ID, tool name, arguments, result, and timestamp.
 */
public class AuditLogger {

    private final Path dbPath;

    public AuditLogger(Path dbPath) {
        this.dbPath = dbPath;
        initDb();
    }

    private void initDb() {
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (IOException ignored) {}

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS audit_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp TEXT NOT NULL,
                    session_id TEXT,
                    tool_name TEXT NOT NULL,
                    args_summary TEXT,
                    result_summary TEXT,
                    success INTEGER NOT NULL DEFAULT 1,
                    duration_ms INTEGER,
                    workdir TEXT
                )
                """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_session ON audit_log(session_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_tool ON audit_log(tool_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_log(timestamp)");
        } catch (SQLException e) {
            System.err.println("[AuditLogger] Failed to init DB: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    public void log(String sessionId, String toolName, String argsSummary,
                    String resultSummary, boolean success, long durationMs, Path workdir) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO audit_log (timestamp, session_id, tool_name, args_summary, result_summary, success, duration_ms, workdir) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, sessionId);
            ps.setString(3, toolName);
            ps.setString(4, truncate(argsSummary, 500));
            ps.setString(5, truncate(resultSummary, 500));
            ps.setInt(6, success ? 1 : 0);
            ps.setLong(7, durationMs);
            ps.setString(8, workdir != null ? workdir.toString() : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[AuditLogger] Failed to log: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> queryRecent(int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM audit_log ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("[AuditLogger] Query failed: " + e.getMessage());
        }
        return results;
    }

    public List<Map<String, Object>> queryBySession(String sessionId, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM audit_log WHERE session_id = ? ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, sessionId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("[AuditLogger] Query failed: " + e.getMessage());
        }
        return results;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
