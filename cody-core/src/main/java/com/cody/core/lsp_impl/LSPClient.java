package com.cody.core.lsp_impl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * LSP client .
 *
 * Manages Language Server Protocol connections for static analysis:
 * <ul>
 *   <li>Python — <b>pyright</b> or <b>pylsp</b></li>
 *   <li>TypeScript — <b>typescript-language-server</b> (tsserver)</li>
 *   <li>Go — <b>gopls</b></li>
 * </ul>
 *
 * <p>The full LSP wire protocol (JSON-RPC framed with {@code Content-Length} headers,
 * initialize/shutdown/didOpen/didChange lifecycle, diagnostic subscriptions) is complex.
 * This implementation provides the interface and process lifecycle management. Methods
 * return stubs until the full protocol is implemented.
 */
public class LSPClient {

    /** Supported language server commands. */
    private static final Map<String, String[]> LANGUAGE_COMMANDS = Map.of(
            "python", new String[]{"pyright-langserver", "--stdio"},
            "typescript", new String[]{"typescript-language-server", "--stdio"},
            "go", new String[]{"gopls", "serve"}
    );

    private final Map<String, LspServer> servers = new ConcurrentHashMap<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /**
     * Start language servers for the specified language set.
     *
     * @param languages set of languages to start (e.g. "python", "typescript", "go")
     * @return map of language to success flag
     */
    public Map<String, Boolean> start(Set<String> languages) {
        Map<String, Boolean> results = new LinkedHashMap<>();
        if (languages == null || languages.isEmpty()) return results;

        for (String lang : languages) {
            String[] command = LANGUAGE_COMMANDS.get(lang.toLowerCase());
            if (command == null) {
                results.put(lang, false);
                continue;
            }
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                LspServer server = new LspServer(lang, process);
                servers.put(lang, server);
                results.put(lang, true);
            } catch (IOException e) {
                results.put(lang, false);
            }
        }
        return results;
    }

    /** Stop all language server processes. */
    public void stop() {
        for (LspServer server : servers.values()) {
            try {
                server.close();
            } catch (Exception ignored) {
                // Best-effort cleanup
            }
        }
        servers.clear();
    }

    // ── LSP Queries ───────────────────────────────────────────────────────

    /**
     * Get diagnostics for a file.
     *
     * <p><b>TODO:</b> Send {@code textDocument/didOpen} + subscribe to
     * {@code textDocument/publishDiagnostics} notifications.
     *
     * @param filePath path to the source file
     * @return diagnostic information string, or stub
     */
    public String getDiagnostics(Path filePath) {
        String lang = detectLanguage(filePath);
        if (lang == null || !servers.containsKey(lang)) {
            return "[LSP_NOT_CONFIGURED] No language server available for: " + filePath;
        }
        // TODO: Full LSP diagnostics pipeline
        return "[LSP_NOT_CONFIGURED] Diagnostics for " + filePath.getFileName()
                + " — full LSP protocol not yet implemented. See LSPClient.java TODOs.";
    }

    /**
     * Get the definition location of a symbol.
     *
     * @param filePath source file
     * @param line     0-based line number
     * @param column   0-based column number
     * @return definition location string, or stub
     */
    public String getDefinition(Path filePath, int line, int column) {
        String lang = detectLanguage(filePath);
        if (lang == null || !servers.containsKey(lang)) {
            return "[LSP_NOT_CONFIGURED] No language server available for: " + filePath;
        }
        // TODO: Send textDocument/definition request
        return "[LSP_NOT_CONFIGURED] Definition at " + filePath.getFileName()
                + ":" + line + ":" + column + " — full LSP protocol not yet implemented.";
    }

    /**
     * Find all references to a symbol.
     *
     * @param filePath source file
     * @param line     0-based line number
     * @param column   0-based column number
     * @return references string, or stub
     */
    public String getReferences(Path filePath, int line, int column) {
        String lang = detectLanguage(filePath);
        if (lang == null || !servers.containsKey(lang)) {
            return "[LSP_NOT_CONFIGURED] No language server available for: " + filePath;
        }
        // TODO: Send textDocument/references request
        return "[LSP_NOT_CONFIGURED] References at " + filePath.getFileName()
                + ":" + line + ":" + column + " — full LSP protocol not yet implemented.";
    }

    /**
     * Get hover information for a symbol.
     *
     * @param filePath source file
     * @param line     0-based line number
     * @param column   0-based column number
     * @return hover info string, or stub
     */
    public String getHover(Path filePath, int line, int column) {
        String lang = detectLanguage(filePath);
        if (lang == null || !servers.containsKey(lang)) {
            return "[LSP_NOT_CONFIGURED] No language server available for: " + filePath;
        }
        // TODO: Send textDocument/hover request
        return "[LSP_NOT_CONFIGURED] Hover at " + filePath.getFileName()
                + ":" + line + ":" + column + " — full LSP protocol not yet implemented.";
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Detect the language based on the file extension. */
    private String detectLanguage(Path filePath) {
        String name = filePath.getFileName().toString().toLowerCase();
        if (name.endsWith(".py")) return "python";
        if (name.endsWith(".ts") || name.endsWith(".tsx") || name.endsWith(".js") || name.endsWith(".jsx")) {
            return "typescript";
        }
        if (name.endsWith(".go")) return "go";
        return null;
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    /** Return the set of languages that have a running language server. */
    public Set<String> getActiveLanguages() {
        return Collections.unmodifiableSet(servers.keySet());
    }

    /** Check if any language servers are active. */
    public boolean hasActiveServers() {
        return !servers.isEmpty();
    }

    // ── Inner Class: LspServer ────────────────────────────────────────────

    /**
     * Represents a single language server process.
     */
    public static class LspServer implements AutoCloseable {
        private final String language;
        private final Process process;
        private volatile boolean initialized;

        LspServer(String language, Process process) {
            this.language = language;
            this.process = process;
        }

        public String getLanguage() { return language; }
        public Process getProcess() { return process; }
        public boolean isInitialized() { return initialized; }

        /** Mark this server as initialized (after the LSP initialize handshake completes). */
        public void setInitialized(boolean initialized) { this.initialized = initialized; }

        @Override
        public void close() {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
