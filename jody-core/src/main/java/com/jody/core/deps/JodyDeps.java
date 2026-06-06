package com.jody.core.deps;

import com.jody.core.config.Config;
import com.jody.core.error.JodyErrors.InteractionTimeoutError;
import com.jody.core.interaction.InteractionHandler;
import com.jody.core.interaction.InteractionHandler.InteractionRequest;
import com.jody.core.interaction.InteractionHandler.InteractionResponse;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Dependency injection container  JodyDeps dataclass.
 *
 * Passed to every tool call via RunContext, provides access to all
 * infrastructure: config, permission manager, file history, audit logger, etc.
 */
public class JodyDeps {

    private final Config config;
    private final Path workdir;
    private final List<Path> allowedRoots;
    private final boolean strictReadBoundary;

    // ── Optional subsystems (null = disabled) ────────────────────────────

    private final Object skillManager;          // SkillManager
    private final Object mcpClient;             // MCPClient
    private final Object subAgentManager;       // SubAgentManager
    private final Object lspClient;             // LSPClient
    private final Object auditLogger;           // AuditLogger
    private final Object permissionManager;     // PermissionManager
    private final Object fileHistory;           // FileHistory
    private final List<Map<String, Object>> todoList;  // shared mutable task list
    private final Object memoryStore;           // ProjectMemoryStore
    private final InteractionHandler interactionHandler;
    private final List<BiFunction<String, Map<String, Object>, Map<String, Object>>> beforeToolHooks;
    private final List<BiFunction<String, Map<String, Object>, String>> afterToolHooks;
    private final Set<String> autoApprovedTools;

    private JodyDeps(Builder builder) {
        this.config = builder.config;
        this.workdir = builder.workdir;
        this.allowedRoots = Collections.unmodifiableList(new ArrayList<>(builder.allowedRoots));
        this.strictReadBoundary = builder.strictReadBoundary;
        this.skillManager = builder.skillManager;
        this.mcpClient = builder.mcpClient;
        this.subAgentManager = builder.subAgentManager;
        this.lspClient = builder.lspClient;
        this.auditLogger = builder.auditLogger;
        this.permissionManager = builder.permissionManager;
        this.fileHistory = builder.fileHistory;
        this.todoList = builder.todoList != null ? builder.todoList : Collections.synchronizedList(new ArrayList<>());
        this.memoryStore = builder.memoryStore;
        this.interactionHandler = builder.interactionHandler;
        this.beforeToolHooks = Collections.unmodifiableList(new ArrayList<>(builder.beforeToolHooks));
        this.afterToolHooks = Collections.unmodifiableList(new ArrayList<>(builder.afterToolHooks));
        this.autoApprovedTools = Collections.unmodifiableSet(new HashSet<>(builder.autoApprovedTools));
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public Config getConfig() { return config; }
    public Path getWorkdir() { return workdir; }
    public List<Path> getAllowedRoots() { return allowedRoots; }
    public boolean isStrictReadBoundary() { return strictReadBoundary; }
    public Object getSkillManager() { return skillManager; }
    public Object getMcpClient() { return mcpClient; }
    public Object getSubAgentManager() { return subAgentManager; }
    public Object getLspClient() { return lspClient; }
    public Object getAuditLogger() { return auditLogger; }
    public Object getPermissionManager() { return permissionManager; }
    public Object getFileHistory() { return fileHistory; }
    public List<Map<String, Object>> getTodoList() { return todoList; }
    public Object getMemoryStore() { return memoryStore; }
    public InteractionHandler getInteractionHandler() { return interactionHandler; }
    public List<BiFunction<String, Map<String, Object>, Map<String, Object>>> getBeforeToolHooks() { return beforeToolHooks; }
    public List<BiFunction<String, Map<String, Object>, String>> getAfterToolHooks() { return afterToolHooks; }
    public Set<String> getAutoApprovedTools() { return autoApprovedTools; }

    // ── Typed getters (cast for convenience) ─────────────────────────────

    @SuppressWarnings("unchecked")
    public <T> T getSkillManager(Class<T> type) { return (T) skillManager; }

    @SuppressWarnings("unchecked")
    public <T> T getMcpClient(Class<T> type) { return (T) mcpClient; }

    @SuppressWarnings("unchecked")
    public <T> T getSubAgentManager(Class<T> type) { return (T) subAgentManager; }

    @SuppressWarnings("unchecked")
    public <T> T getLspClient(Class<T> type) { return (T) lspClient; }

    @SuppressWarnings("unchecked")
    public <T> T getAuditLogger(Class<T> type) { return (T) auditLogger; }

    @SuppressWarnings("unchecked")
    public <T> T getPermissionManager(Class<T> type) { return (T) permissionManager; }

    @SuppressWarnings("unchecked")
    public <T> T getFileHistory(Class<T> type) { return (T) fileHistory; }

    @SuppressWarnings("unchecked")
    public <T> T getMemoryStore(Class<T> type) { return (T) memoryStore; }

    // ── Builder ──────────────────────────────────────────────────────────

    public static class Builder {
        private Config config;
        private Path workdir;
        private List<Path> allowedRoots = new ArrayList<>();
        private boolean strictReadBoundary;
        private Object skillManager;
        private Object mcpClient;
        private Object subAgentManager;
        private Object lspClient;
        private Object auditLogger;
        private Object permissionManager;
        private Object fileHistory;
        private List<Map<String, Object>> todoList = Collections.synchronizedList(new ArrayList<>());
        private Object memoryStore;
        private InteractionHandler interactionHandler;
        private List<BiFunction<String, Map<String, Object>, Map<String, Object>>> beforeToolHooks = new ArrayList<>();
        private List<BiFunction<String, Map<String, Object>, String>> afterToolHooks = new ArrayList<>();
        private Set<String> autoApprovedTools = new HashSet<>();

        public Builder(Config config, Path workdir) {
            this.config = config;
            this.workdir = workdir;
        }

        public Builder allowedRoots(List<Path> roots) { this.allowedRoots = roots; return this; }
        public Builder strictReadBoundary(boolean v) { this.strictReadBoundary = v; return this; }
        public Builder skillManager(Object sm) { this.skillManager = sm; return this; }
        public Builder mcpClient(Object mc) { this.mcpClient = mc; return this; }
        public Builder subAgentManager(Object sam) { this.subAgentManager = sam; return this; }
        public Builder lspClient(Object lc) { this.lspClient = lc; return this; }
        public Builder auditLogger(Object al) { this.auditLogger = al; return this; }
        public Builder permissionManager(Object pm) { this.permissionManager = pm; return this; }
        public Builder fileHistory(Object fh) { this.fileHistory = fh; return this; }
        public Builder todoList(List<Map<String, Object>> tl) { this.todoList = tl; return this; }
        public Builder memoryStore(Object ms) { this.memoryStore = ms; return this; }
        public Builder interactionHandler(InteractionHandler ih) { this.interactionHandler = ih; return this; }
        public Builder addBeforeToolHook(BiFunction<String, Map<String, Object>, Map<String, Object>> hook) {
            this.beforeToolHooks.add(hook); return this;
        }
        public Builder addAfterToolHook(BiFunction<String, Map<String, Object>, String> hook) {
            this.afterToolHooks.add(hook); return this;
        }
        public Builder addAutoApprovedTool(String toolName) {
            this.autoApprovedTools.add(toolName); return this;
        }

        public JodyDeps build() {
            return new JodyDeps(this);
        }
    }
}
