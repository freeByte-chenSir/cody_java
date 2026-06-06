package com.cody.core.tool.agent;

import com.cody.core.deps.CodyDeps;
import com.cody.core.tool.CodyTool;
import com.cody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gets the status and result of a sub-agent.
 *
 */
public class GetAgentStatusTool implements CodyTool {

    @Override public String getName() { return "get_agent_status"; }

    @Override public String getDescription() {
        return "Get the current status and result of a sub-agent by its agent_id.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("agent_id", Map.of("type", "string", "description", "The agent ID to check status for"));
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
                    .getMethod("getAgentStatus", String.class)
                    .invoke(subAgentManager, agentId);
            return result != null ? result.toString()
                    : "[AGENT_NOT_FOUND] Agent '" + agentId + "' not found.";
        } catch (Exception e) {
            return "[SUB_AGENT_NOT_AVAILABLE] Cannot get status for agent '" + agentId + "': "
                    + e.getMessage();
        }
    }
}
