package com.cody.core.tool.memory;

import com.cody.core.deps.CodyDeps;
import com.cody.core.memory_impl.ProjectMemory;
import com.cody.core.tool.CodyTool;
import com.cody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Save a memory entry for cross-session persistence.
 *
 */
public class SaveMemoryTool implements CodyTool {

    @Override public String getName() { return "save_memory"; }

    @Override public String getDescription() {
        return "Save a piece of information to persistent memory for future sessions. "
                + "Categories: conventions, patterns, issues, decisions.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("category", Map.of("type", "string",
                "description", "Memory category: conventions, patterns, issues, decisions"));
        props.put("content", Map.of("type", "string",
                "description", "The content to remember"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("category", "content"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String category = (String) arguments.get("category");
        String content = (String) arguments.get("content");
        CodyDeps deps = ctx.getDeps(CodyDeps.class);

        // Try typed getter first
        ProjectMemory memory = deps != null ? deps.getMemoryStore(ProjectMemory.class) : null;

        if (memory == null) {
            return "[MEMORY_NOT_AVAILABLE] Project memory store is not configured. "
                    + "Content not saved: " + truncate(content, 80);
        }

        try {
            memory.save(category, content);
            return "Memory saved to category '" + category + "': " + truncate(content, 100);
        } catch (IllegalArgumentException e) {
            return "[MEMORY_ERROR] " + e.getMessage();
        } catch (Exception e) {
            return "[MEMORY_ERROR] Failed to save: " + e.getMessage();
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
