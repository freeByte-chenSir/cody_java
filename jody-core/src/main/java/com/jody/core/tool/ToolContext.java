package com.jody.core.tool;

import java.nio.file.Path;

/**
 * Tool execution context providing workdir, tool name, and access to dependencies.
 * Passed to every tool's {@code execute()} method for accessing the dependency container.
 */
public class ToolContext {

    private final Path workdir;
    private final String toolName;
    private final Object deps; // JodyDeps

    public ToolContext(Path workdir, String toolName, Object deps) {
        this.workdir = workdir;
        this.toolName = toolName;
        this.deps = deps;
    }

    public Path getWorkdir() { return workdir; }
    public String getToolName() { return toolName; }

    @SuppressWarnings("unchecked")
    public <T> T getDeps(Class<T> type) { return (T) deps; }

    /** Resolve a path relative to workdir. Absolute paths returned as-is. */
    public Path resolvePath(String path) {
        Path p = Path.of(path);
        return p.isAbsolute() ? p.normalize() : workdir.resolve(path).normalize();
    }

    /** Check if a resolved path is within workdir. */
    public boolean isPathSafe(Path resolved) {
        return resolved.startsWith(workdir.normalize());
    }
}
