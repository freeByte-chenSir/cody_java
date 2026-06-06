package com.cody.core.tool.history;

import com.cody.core.deps.CodyDeps;
import com.cody.core.tool.CodyTool;
import com.cody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redo the last undone file modification. Optionally filtered by path.
 *
 */
public class RedoFileTool implements CodyTool {

    @Override public String getName() { return "redo_file"; }

    @Override public String getDescription() {
        return "Redo the last undone file modification. If no path is given, redoes the most recent undo.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string", "description", "Path to redo last undo for (optional, redoes most recent if omitted)"));
        return props;
    }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String path = (String) arguments.get("path");
        CodyDeps deps = ctx.getDeps(CodyDeps.class);
        Object fileHistory = deps != null ? deps.getFileHistory() : null;

        if (fileHistory == null) {
            return "[FILE_HISTORY_NOT_AVAILABLE] File history is not enabled.";
        }

        try {
            Object result;
            if (path != null && !path.isEmpty()) {
                result = fileHistory.getClass()
                        .getMethod("redo", String.class)
                        .invoke(fileHistory, path);
            } else {
                result = fileHistory.getClass()
                        .getMethod("redo")
                        .invoke(fileHistory);
            }
            return result != null ? result.toString() : "Redo completed.";
        } catch (Exception e) {
            return "[FILE_HISTORY_NOT_AVAILABLE] Cannot redo: " + e.getMessage();
        }
    }
}
