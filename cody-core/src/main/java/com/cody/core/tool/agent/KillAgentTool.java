package com.cody.core.tool.agent;

import com.cody.core.deps.CodyDeps;
import com.cody.core.tool.CodyTool;
import com.cody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kills a running sub-agent.
 *
 */
public class KillAgentTool implements CodyTool {

    @Override public String getName() { return "kill_agent"; }

    @Override public String getDescription() {
        return "Kill a running sub-agent by its agent_id.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("agent_id", Map.of("type", "string", "description", "The agent ID to kill"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("agent_id"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String agentId = (String) arguments.get("agent_id");
        CodyDeps deps = ctx.getDeps(CodyDeps.class);
        Object subAgentManager = deps != null ? deps.getSubAgentManager() : null;

        if (subAgentManager == null) {
            return "[SUB_AGENT_NOT_AVAILABLE] Sub-agent manager is not configured.";
        }

        try {
            Object result = subAgentManager.getClass()
                    .getMethod("killAgent", String.class)
                    .invoke(subAgentManager, agentId);
            return result != null ? result.toString() : "Agent '" + agentId + "' killed.";
        } catch (Exception e) {
            return "[SUB_AGENT_NOT_AVAILABLE] Cannot kill agent '" + agentId + "': "
                    + e.getMessage();
        }
    }
}
