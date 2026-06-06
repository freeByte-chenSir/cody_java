package com.cody.core.tool.history;

import com.cody.core.deps.CodyDeps;
import com.cody.core.tool.CodyTool;
import com.cody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Undo the last file modification. Optionally filtered by path.
 *
 */
public class UndoFileTool implements CodyTool {

    @Override public String getName() { return "undo_file"; }

    @Override public String getDescription() {
        return "Undo the last file modification. If no path is given, undoes the most recent change.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string", "description", "Path to undo last change for (optional, undoes most recent if omitted)"));
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
                        .getMethod("undo", String.class)
                        .invoke(fileHistory, path);
            } else {
                result = fileHistory.getClass()
                        .getMethod("undo")
                        .invoke(fileHistory);
            }
            return result != null ? result.toString() : "Undo completed.";
        } catch (Exception e) {
            return "[FILE_HISTORY_NOT_AVAILABLE] Cannot undo: " + e.getMessage();
        }
    }
}
