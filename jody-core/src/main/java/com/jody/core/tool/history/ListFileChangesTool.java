package com.jody.core.tool.history;

import com.jody.core.deps.JodyDeps;
import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lists recent file changes from the file history.
 *
 */
public class ListFileChangesTool implements JodyTool {

    @Override public String getName() { return "list_file_changes"; }

    @Override public String getDescription() {
        return "List recent file modifications. If no path is given, lists all recent changes.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string", "description", "Path to list changes for (optional, lists all if omitted)"));
        return props;
    }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String path = (String) arguments.get("path");
        JodyDeps deps = ctx.getDeps(JodyDeps.class);
        Object fileHistory = deps != null ? deps.getFileHistory() : null;

        if (fileHistory == null) {
            return "[FILE_HISTORY_NOT_AVAILABLE] File history is not enabled.";
        }

        try {
            Object result;
            if (path != null && !path.isEmpty()) {
                result = fileHistory.getClass()
                        .getMethod("listChanges", String.class)
                        .invoke(fileHistory, path);
            } else {
                result = fileHistory.getClass()
                        .getMethod("listChanges")
                        .invoke(fileHistory);
            }
            if (result == null) {
                return "No recent file changes.";
            }
            return result.toString();
        } catch (Exception e) {
            return "[FILE_HISTORY_NOT_AVAILABLE] Cannot list changes: " + e.getMessage();
        }
    }
}
