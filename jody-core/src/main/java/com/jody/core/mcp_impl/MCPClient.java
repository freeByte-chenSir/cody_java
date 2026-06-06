package com.jody.core.mcp_impl;

import com.jody.core.config.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MCP client .
 *
 * Manages Model Context Protocol server connections via two transports:
 * <ul>
 *   <li><b>stdio</b> — spawn a subprocess, communicate via stdin/stdout JSON-RPC</li>
 *   <li><b>HTTP</b> — POST JSON-RPC requests to a remote MCP server</li>
 * </ul>
 *
 * <p>Full MCP protocol (handshakes, capability negotiation, notification routing)
 * is complex. This implementation provides the interface and connection lifecycle
 * management, with stub responses for actual tool calls. Backfilling the complete
 * JSON-RPC logic is deferred (see TODOs).
 */
public class MCPClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    private final Config config;
    private final Map<String, McpServerConnection> connections = new ConcurrentHashMap<>();
    private final OkHttpClient httpClient;

    public MCPClient(Config config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(60))
                .build();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /**
     * Start all configured MCP servers from config.
     *
     * @param servers list of server configs (name, transport, command/url, etc.)
     * @return map of server name to success flag
     */
    public Map<String, Boolean> startAll(List<Config.MCPServerConfig> servers) {
        Map<String, Boolean> results = new LinkedHashMap<>();
        if (servers == null || servers.isEmpty()) return results;

        for (Config.MCPServerConfig sc : servers) {
            try {
                McpServerConnection conn = startOne(sc);
                connections.put(sc.getName(), conn);
                results.put(sc.getName(), true);
            } catch (Exception e) {
                results.put(sc.getName(), false);
            }
        }
        return results;
    }

    /** Start a single MCP server. */
    private McpServerConnection startOne(Config.MCPServerConfig sc) throws IOException {
        if ("http".equalsIgnoreCase(sc.getTransport())) {
            return new McpServerConnection(sc.getName(), "http", null, sc.getUrl(), sc.getHeaders());
        } else {
            // Default: stdio
            ProcessBuilder pb = new ProcessBuilder(sc.getCommand());
            if (sc.getArgs() != null) pb.command().addAll(sc.getArgs());
            if (sc.getEnv() != null) pb.environment().putAll(sc.getEnv());
            Process process = pb.start();
            return new McpServerConnection(sc.getName(), "stdio", process, null, null);
        }
    }

    /** Stop all managed MCP server connections. */
    public void stopAll() {
        for (McpServerConnection conn : connections.values()) {
            try {
                conn.close();
            } catch (Exception ignored) {
                // Best-effort cleanup
            }
        }
        connections.clear();
    }

    // ── Tool Operations ───────────────────────────────────────────────────

    /**
     * Call a tool on a specific MCP server.
     *
     * <p><b>TODO:</b> Implement full JSON-RPC handshake and tool invocation:
     * <ol>
     *   <li>Send {@code initialize} request, receive server capabilities</li>
     *   <li>Send {@code tools/call} request with tool name + arguments</li>
     *   <li>Parse the JSON-RPC response and extract the tool result</li>
     * </ol>
     *
     * @param serverName the MCP server name
     * @param toolName   the tool to invoke
     * @param arguments  tool arguments as a flat map
     * @return tool result string, or {@code "[MCP_NOT_CONFIGURED]"} if not implemented
     */
    @SuppressWarnings("unchecked")
    public String callTool(String serverName, String toolName, Map<String, Object> arguments) {
        McpServerConnection conn = connections.get(serverName);
        if (conn == null) {
            return "[MCP_ERROR] Server '" + serverName + "' not found or not started";
        }

        // TODO: Wire full MCP JSON-RPC protocol
        // Stub — return placeholder
        return "[MCP_NOT_CONFIGURED] Tool '" + toolName + "' on server '" + serverName
                + "' — full MCP protocol not yet implemented. See MCPClient.java TODOs.";
    }

    /**
     * List all tools from all connected MCP servers as a JSON string.
     *
     * <p><b>TODO:</b> Send {@code tools/list} requests to each server and aggregate.
     *
     * @return JSON array of tool definitions, or empty array stub
     */
    public String listTools() {
        // TODO: Implement tools/list JSON-RPC call per connected server
        if (connections.isEmpty()) return "[]";

        // Stub — aggregate placeholder for each connected server
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, McpServerConnection> e : connections.entrySet()) {
            if (!first) sb.append(",");
            sb.append("{\"server\":\"").append(e.getKey())
                    .append("\",\"tools\":\"[MCP_NOT_CONFIGURED]\"}");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Get a specific tool definition from a server.
     *
     * @param serverName the MCP server name
     * @param toolName   the tool to look up
     * @return JSON tool definition string, or stub
     */
    public String getTool(String serverName, String toolName) {
        if (!connections.containsKey(serverName)) {
            return "{\"error\":\"Server '" + serverName + "' not found\"}";
        }
        // TODO: Implement tools/get or filter from tools/list
        return "[MCP_NOT_CONFIGURED] Tool '" + toolName + "' on server '" + serverName + "'";
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    /** Return the names of all currently connected MCP servers. */
    public Set<String> getConnectedServers() {
        return Collections.unmodifiableSet(connections.keySet());
    }

    /** Return the number of active connections. */
    public int getConnectionCount() {
        return connections.size();
    }

    // ── Inner Class: McpServerConnection ──────────────────────────────────

    /**
     * Represents a single MCP server connection, holding the process handle
     * (for stdio transport) or the URL &amp; headers (for HTTP transport).
     */
    public static class McpServerConnection implements AutoCloseable {
        private final String name;
        private final String transport;
        private final Process process;
        private final String url;
        private final Map<String, String> headers;

        McpServerConnection(String name, String transport, Process process,
                            String url, Map<String, String> headers) {
            this.name = name;
            this.transport = transport;
            this.process = process;
            this.url = url;
            this.headers = headers != null ? Collections.unmodifiableMap(new HashMap<>(headers)) : Map.of();
        }

        public String getName() { return name; }
        public String getTransport() { return transport; }
        public Process getProcess() { return process; }
        public String getUrl() { return url; }
        public Map<String, String> getHeaders() { return headers; }

        @Override
        public void close() {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try { process.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
