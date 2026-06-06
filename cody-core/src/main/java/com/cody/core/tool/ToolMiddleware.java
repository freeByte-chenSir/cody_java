package com.cody.core.tool;

import com.cody.core.config.Config;
import com.cody.core.deps.CodyDeps;
import com.cody.core.error.CodyErrors.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Tool execution middleware .
 *
 * Wraps every tool call with:
 *   1. before_tool hooks (can cancel or modify args)
 *   2. Permission check (allow/deny/confirm)
 *   3. Tool execution + error handling (ToolError → ModelRetry, Exception → error string)
 *   4. Output truncation
 *   5. after_tool hooks (can transform result)
 */
public class ToolMiddleware {

    /**
     * Check if a tool is permitted to run.
     *
     * Permission levels: allow (proceed), deny (raise ToolPermissionDenied), confirm (block until human responds).
     */
    public static void checkPermission(CodyDeps deps, String toolName, String argsSummary) {
        Object pm = deps.getPermissionManager();
        if (pm == null) return; // No permission manager = allow all

        // TODO: Check PermissionManager when implemented
        // For now, allow all tools to run
    }

    /**
     * Resolve and validate a file path.
     *
     * @param workdir           Working directory
     * @param path              User-supplied path (relative or absolute)
     * @param allowReadOutside  Whether reads outside workdir are permitted
     * @param allowedRoots      Additional allowed filesystem roots
     * @return Resolved absolute path
     * @throws ToolPathDenied if path is outside allowed boundaries
     */
    public static Path resolveAndCheck(Path workdir, String path, boolean allowReadOutside,
                                        List<Path> allowedRoots) {
        Path resolved = Path.of(path);
        if (!resolved.isAbsolute()) {
            resolved = workdir.resolve(path);
        }
        resolved = resolved.normalize();

        // Check if within workdir
        if (resolved.startsWith(workdir.normalize())) {
            return resolved;
        }

        // Check allowed roots
        if (allowedRoots != null) {
            for (Path root : allowedRoots) {
                if (resolved.startsWith(root.toAbsolutePath().normalize())) {
                    return resolved;
                }
            }
        }

        // Allow read outside for read-only operations
        if (allowReadOutside) {
            return resolved;
        }

        throw new ToolPathDenied(path, workdir.toString());
    }

    /**
     * Truncate tool output if too long.
     */
    public static String truncate(String result, Config config) {
        if (config == null || !config.getTruncation().isEnabled()) return result;
        int maxChars = config.getTruncation().getMaxOutputChars();
        if (result.length() <= maxChars) return result;
        return result.substring(0, maxChars)
                + "\n\n[...output truncated at " + maxChars + " characters...]";
    }

    /**
     * Execute a tool with full middleware pipeline.
     *
     * @param tool      The tool to execute
     * @param ctx       Tool execution context
     * @param arguments Tool arguments from LLM
     * @param deps      Dependency injection container
     * @return Tool result string
     */
    public static String execute(CodyTool tool, ToolContext ctx,
                                  Map<String, Object> arguments, CodyDeps deps) {
        String toolName = tool.getName();
        Map<String, Object> args = arguments;

        // Step 1: before_tool hooks
        for (BiFunction<String, Map<String, Object>, Map<String, Object>> hook : deps.getBeforeToolHooks()) {
            Map<String, Object> result = hook.apply(toolName, new java.util.HashMap<>(args));
            if (result == null) {
                throw new ToolPermissionDenied("Tool call cancelled by before_tool hook", toolName);
            }
            args = result;
        }

        // Step 2: Permission check
        checkPermission(deps, toolName, summarizeArgs(args));

        // Step 3: Execute tool with error handling
        String result;
        try {
            result = tool.execute(ctx, args);
        } catch (ToolError e) {
            // ToolError → re-raise; downstream converts to ModelRetry so LLM can fix params
            throw e;
        } catch (Exception e) {
            // Unknown exception → return error string to the LLM (graceful degradation)
            return "[ERROR] " + toolName + " failed: " + e.getMessage();
        }

        // Step 4: Truncate output
        result = truncate(result, deps.getConfig());

        // Step 5: after_tool hooks (can transform result)
        for (BiFunction<String, Map<String, Object>, String> hook : deps.getAfterToolHooks()) {
            result = hook.apply(toolName, new java.util.HashMap<>(args));
            if (result == null) {
                result = "[HOOK ERROR] after_tool hook returned null for: " + toolName;
            }
        }

        return result;
    }

    /** Generate a brief summary of tool arguments for logging/audit. */
    private static String summarizeArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : args.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            String val = String.valueOf(e.getValue());
            if (val.length() > 60) val = val.substring(0, 57) + "...";
            sb.append(e.getKey()).append("=").append(val);
        }
        return sb.toString();
    }
}
