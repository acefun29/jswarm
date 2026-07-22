// SQLite 会话持久化
package com.jswarm.examples.showcase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jswarm.adapter.lc4j.run.ChatMessageCodec;
import com.jswarm.core.SwarmContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ShowcaseSessionStore implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String jdbcUrl;
    private final Duration ttl = Duration.ofHours(1);

    public ShowcaseSessionStore(Path dbPath) throws Exception {
        if (dbPath.getParent() != null) {
            Files.createDirectories(dbPath.getParent());
        }
        jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        try (Connection connection = openConnection()) {
            initSchema(connection);
        }
    }

    private Connection openConnection() throws Exception {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement pragma = connection.createStatement()) {
            pragma.execute("PRAGMA journal_mode=WAL");
        }
        return connection;
    }

    private void initSchema(Connection connection) throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS showcase_session (
                        session_id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        current_agent_id TEXT NOT NULL,
                        entry_hook_fired INTEGER NOT NULL,
                        context_json TEXT NOT NULL,
                        history_json TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        version INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            try {
                stmt.execute("ALTER TABLE showcase_session ADD COLUMN version INTEGER NOT NULL DEFAULT 0");
            } catch (Exception ignored) {
            }
        }
    }

    public Optional<ShowcaseSession> load(String sessionId, String entryAgentId) {
        return load(sessionId, entryAgentId, null);
    }

    public Optional<ShowcaseSession> load(String sessionId, String entryAgentId, String ownerId) {
        try (Connection connection = openConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT user_id, current_agent_id, entry_hook_fired, context_json, history_json, updated_at, version "
                             + "FROM showcase_session WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String storedOwner = rs.getString("user_id");
                if (ownerId != null && !ownerId.equals(storedOwner)) {
                    return Optional.empty();
                }
                Instant updated = Instant.parse(rs.getString("updated_at"));
                if (updated.plus(ttl).isBefore(Instant.now())) {
                    delete(sessionId);
                    return Optional.empty();
                }
                SwarmContext ctx = decodeContext(rs.getString("context_json"));
                ShowcaseSession session = new ShowcaseSession(sessionId, entryAgentId, ctx);
                session.setCurrentAgentId(rs.getString("current_agent_id"));
                session.setEntryHookFired(rs.getInt("entry_hook_fired") == 1);
                session.setHistory(ChatMessageCodec.decode(rs.getString("history_json")));
                session.setVersion(rs.getLong("version"));
                return Optional.of(session);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load session: " + sessionId, e);
        }
    }

    public synchronized void save(ShowcaseSession session) {
        try (Connection connection = openConnection()) {
            String userId = session.context().get("user_id", String.class);
            if (userId == null) {
                userId = "u001";
            }
            String contextJson = encodeContext(session.context());
            String historyJson = ChatMessageCodec.encode(session.history());
            connection.setAutoCommit(false);
            long nextVersion;
            boolean existing;
            try (PreparedStatement current = connection.prepareStatement(
                    "SELECT version FROM showcase_session WHERE session_id = ?")) {
                current.setString(1, session.sessionId());
                try (ResultSet rs = current.executeQuery()) {
                    if (rs.next()) {
                        long storedVersion = rs.getLong(1);
                        if (storedVersion != session.version()) {
                            throw new IllegalStateException("Session version conflict: " + session.sessionId());
                        }
                        existing = true;
                        nextVersion = storedVersion + 1;
                    } else {
                        existing = false;
                        nextVersion = 1;
                    }
                }
            }
            String sql = existing ? """
                    UPDATE showcase_session SET user_id = ?, current_agent_id = ?, entry_hook_fired = ?,
                        context_json = ?, history_json = ?, updated_at = ?, version = ?
                    WHERE session_id = ? AND version = ?
                    """ : """
                    INSERT INTO showcase_session
                    (session_id, user_id, current_agent_id, entry_hook_fired, context_json, history_json, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int i = 1;
                if (existing) {
                    ps.setString(i++, userId);
                    ps.setString(i++, session.currentAgentId());
                    ps.setInt(i++, session.entryHookFired() ? 1 : 0);
                    ps.setString(i++, contextJson);
                    ps.setString(i++, historyJson);
                    ps.setString(i++, Instant.now().toString());
                    ps.setLong(i++, nextVersion);
                    ps.setString(i++, session.sessionId());
                    ps.setLong(i, session.version());
                } else {
                    ps.setString(i++, session.sessionId());
                    ps.setString(i++, userId);
                    ps.setString(i++, session.currentAgentId());
                    ps.setInt(i++, session.entryHookFired() ? 1 : 0);
                    ps.setString(i++, contextJson);
                    ps.setString(i++, historyJson);
                    ps.setString(i++, Instant.now().toString());
                    ps.setLong(i, nextVersion);
                }
                ps.executeUpdate();
            }
            connection.commit();
            session.setVersion(nextVersion);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save session: " + session.sessionId(), e);
        }
    }

    public void delete(String sessionId) {
        try (Connection connection = openConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM showcase_session WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete session: " + sessionId, e);
        }
    }

    private static String encodeContext(SwarmContext ctx) throws Exception {
        return MAPPER.writeValueAsString(ctx.asMap());
    }

    private static SwarmContext decodeContext(String json) throws Exception {
        SwarmContext ctx = new SwarmContext();
        Map<String, Object> map = MAPPER.readValue(json, new TypeReference<>() {
        });
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if ("total_spent".equals(entry.getKey()) && value instanceof Number number) {
                ctx.put(entry.getKey(), number.intValue());
            } else {
                ctx.put(entry.getKey(), value);
            }
        }
        return ctx;
    }

    @Override
    public void close() {
    }
}
