package com.jody.core.tool.mcp;

import com.jody.core.deps.JodyDeps;
import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls a tool on an MCP server.
 *
 */
public class McpCallTool implements JodyTool {

    @Override public String getName() { return "mcp_call"; }

    @Override public String getDescription() {
        return "Call a tool on an MCP server. Provide server name, tool name, and arguments.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("server", Map.of("type", "string", "description", "Name of the MCP server"));
        props.put("tool", Map.of("type", "string", "description", "Name of the tool to call on the server"));
        props.put("arguments", Map.of("type", "object", "description", "Arguments to pass to the tool (JSON object)"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("server", "tool"); }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String server = (String) arguments.get("server");
        String tool = (String) arguments.get("tool");
        Map<String, Object> toolArgs = (Map<String, Object>) arguments.getOrDefault("arguments", Map.of());
        JodyDeps deps = ctx.getDeps(JodyDeps.class);
        Object mcpClient = deps != null ? deps.getMcpClient() : null;

        if (mcpClient == null) {
            return "[MCP_NOT_AVAILABLE] MCP client is not configured. "
                    + "Cannot call tool '" + tool + "' on server '" + server + "'.";
        }

        try {
            Object result = mcpClient.getClass()
                    .getMethod("callTool", String.class, String.class, Map.class)
                    .invoke(mcpClient, server, tool, toolArgs);
            return result != null ? result.toString() : "[MCP_CALL_COMPLETED]";
        } catch (Exception e) {
            return "[MCP_ERROR] Failed to call tool '" + tool + "' on server '" + server + "': "
                    + e.getMessage();
        }
    }
}
