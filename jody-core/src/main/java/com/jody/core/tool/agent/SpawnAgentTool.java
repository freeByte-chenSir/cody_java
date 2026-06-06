package com.jody.core.tool.agent;

import com.jody.core.deps.JodyDeps;
import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spawns a sub-agent to handle a delegated task.
 *
 */
public class SpawnAgentTool implements JodyTool {

    @Override public String getName() { return "spawn_agent"; }

    @Override public String getDescription() {
        return "Spawn a sub-agent to handle a delegated task. "
                + "Types: code (full file+search+command), research (read-only), test (read+write+exec), generic (full).";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("task", Map.of("type", "string", "description", "The task description for the sub-agent"));
        props.put("type", Map.of("type", "string", "description", "Agent type: code, research, test, or generic",
                "enum", List.of("code", "research", "test", "generic")));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("task", "type"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String task = (String) arguments.get("task");
        String type = (String) arguments.get("type");
        JodyDeps deps = ctx.getDeps(JodyDeps.class);
        Object subAgentManager = deps != null ? deps.getSubAgentManager() : null;

        if (subAgentManager == null) {
            return "[SUB_AGENT_NOT_AVAILABLE] Sub-agent manager is not configured. "
                    + "Task '" + task + "' of type '" + type + "' could not be spawned.";
        }

        try {
            Object result = subAgentManager.getClass()
                    .getMethod("spawnAgent", String.class, String.class)
                    .invoke(subAgentManager, task, type);
            return result != null ? result.toString() : "[AGENT_SPAWNED]";
        } catch (Exception e) {
            return "[SUB_AGENT_NOT_AVAILABLE] Sub-agent manager available but spawnAgent() not yet callable: "
                    + e.getMessage();
        }
    }
}
