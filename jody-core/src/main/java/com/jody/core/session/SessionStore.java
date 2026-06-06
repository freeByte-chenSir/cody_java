package com.jody.core.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * SQLite session persistence  SessionStore.
 *
 * Stores session metadata (id, title, model, timestamps) and messages.
 */
public class SessionStore {

    private final Path dbPath;
    private final ObjectMapper mapper = new ObjectMapper();

    public SessionStore(Path dbPath) {
        this.dbPath = dbPath;
        initDb();
    }

    private void initDb() {
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (IOException ignored) {}

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id TEXT PRIMARY KEY,
                    title TEXT,
                    model TEXT,
                    workdir TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'active',
                    metadata TEXT
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    timestamp TEXT NOT NULL,
                    FOREIGN KEY (session_id) REFERENCES sessions(id)
                )
                """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_msg_session ON messages(session_id)");
        } catch (SQLException e) {
            System.err.println("[SessionStore] Failed to init DB: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    // ── Sessions CRUD ──────────────────────────────────────────────────

    public Map<String, Object> createSession(String id, String title, String model, Path workdir) {
        String now = Instant.now().toString();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO sessions (id, title, model, workdir, created_at, updated_at, status) "
                             + "VALUES (?, ?, ?, ?, ?, ?, 'active')")) {
            ps.setString(1, id);
            ps.setString(2, title);
            ps.setString(3, model);
            ps.setString(4, workdir != null ? workdir.toString() : null);
            ps.setString(5, now);
            ps.setString(6, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[SessionStore] Create session failed: " + e.getMessage());
        }
        return getSession(id);
    }

    public Map<String, Object> getSession(String id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM sessions WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowToMap(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[SessionStore] Get session failed: " + e.getMessage());
        }
        return null;
    }

    public List<Map<String, Object>> listSessions(int limit, int offset) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM sessions ORDER BY updated_at DESC LIMIT ? OFFSET ?")) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rowToMap(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[SessionStore] List sessions failed: " + e.getMessage());
        }
        return results;
    }

    public void updateSession(String id, String title, String status) {
        String now = Instant.now().toString();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE sessions SET title = COALESCE(?, title), status = COALESCE(?, status), updated_at = ? WHERE id = ?")) {
            ps.setString(1, title);
            ps.setString(2, status);
            ps.setString(3, now);
            ps.setString(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[SessionStore] Update session failed: " + e.getMessage());
        }
    }

    public void deleteSession(String id) {
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM messages WHERE session_id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM sessions WHERE id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[SessionStore] Delete session failed: " + e.getMessage());
        }
    }

    // ── Messages ───────────────────────────────────────────────────────

    public void saveMessage(String sessionId, String role, String content) {
        String now = Instant.now().toString();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO messages (session_id, role, content, timestamp) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, sessionId);
            ps.setString(2, role);
            ps.setString(3, content);
            ps.setString(4, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[SessionStore] Save message failed: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getMessages(String sessionId, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM messages WHERE session_id = ? ORDER BY id ASC LIMIT ?")) {
            ps.setString(1, sessionId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rowToMap(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[SessionStore] Get messages failed: " + e.getMessage());
        }
        return results;
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            row.put(meta.getColumnName(i), rs.getObject(i));
        }
        return row;
    }
}
