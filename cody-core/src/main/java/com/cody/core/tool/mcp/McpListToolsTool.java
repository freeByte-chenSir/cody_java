package com.cody.core.tool.mcp;

import com.cody.core.deps.CodyDeps;
import com.cody.core.tool.CodyTool;
import com.cody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lists all tools from all configured MCP servers.
 *
 */
public class McpListToolsTool implements CodyTool {

    @Override public String getName() { return "mcp_list_tools"; }

    @Override public String getDescription() {
        return "List all available tools from all configured MCP servers.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return new LinkedHashMap<>(); // no parameters
    }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        CodyDeps deps = ctx.getDeps(CodyDeps.class);
        Object mcpClient = deps != null ? deps.getMcpClient() : null;

        if (mcpClient == null) {
            return "[MCP_NOT_AVAILABLE] No MCP servers are configured.";
        }

        try {
            Object result = mcpClient.getClass()
                    .getMethod("listTools")
                    .invoke(mcpClient);
            if (result == null) {
                return "[MCP_NOT_AVAILABLE] No tools found from MCP servers.";
            }
            return result.toString();
        } catch (Exception e) {
            return "[MCP_NOT_AVAILABLE] MCP client available but listTools() not yet callable: "
                    + e.getMessage();
        }
    }
}
