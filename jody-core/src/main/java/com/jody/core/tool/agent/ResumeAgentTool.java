package com.jody.core.tool.agent;

import com.jody.core.deps.JodyDeps;
import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resumes a completed, failed, or timed-out sub-agent.
 *
 */
public class ResumeAgentTool implements JodyTool {

    @Override public String getName() { return "resume_agent"; }

    @Override public String getDescription() {
        return "Resume a sub-agent that has completed, failed, or timed out, by its agent_id.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("agent_id", Map.of("type", "string", "description", "The agent ID to resume"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("agent_id"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String agentId = (String) arguments.get("agent_id");
        JodyDeps deps = ctx.getDeps(JodyDeps.class);
        Object subAgentManager = deps != null ? deps.getSubAgentManager() : null;

        if (subAgentManager == null) {
            return "[SUB_AGENT_NOT_AVAILABLE] Sub-agent manager is not configured.";
        }

        try {
            Object result = subAgentManager.getClass()
                    .getMethod("resumeAgent", String.class)
                    .invoke(subAgentManager, agentId);
            return result != null ? result.toString() : "Agent '" + agentId + "' resumed.";
        } catch (Exception e) {
            return "[SUB_AGENT_NOT_AVAILABLE] Cannot resume agent '" + agentId + "': "
                    + e.getMessage();
        }
    }
}
