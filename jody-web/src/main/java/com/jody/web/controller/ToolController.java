package com.jody.web.controller;

import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;
import com.jody.core.tool.ToolMiddleware;
import com.jody.core.tool.ToolRegistry;
import com.jody.sdk.JodyClient;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.*;

/**
 * Tool endpoints.
 *
 * GET  /tool              — list all registered tools
 * POST /tool/{toolName}   — execute a tool directly
 */
@RestController
@RequestMapping("/tool")
public class ToolController {

    private final JodyClient client;

    public ToolController(JodyClient client) {
        this.client = client;
    }

    /** List all registered tools. */
    @GetMapping
    public List<Map<String, Object>> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (JodyTool tool : ToolRegistry.CORE_TOOLS) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", tool.getName());
            info.put("description", tool.getDescription());
            info.put("parameters", tool.getParametersSchema());
            tools.add(info);
        }
        return tools;
    }

    /** Execute a tool directly. */
    @PostMapping("/{toolName}")
    public Map<String, Object> executeTool(@PathVariable String toolName, @RequestBody Map<String, Object> body) {
        JodyTool tool = ToolRegistry.getTool(toolName);
        Map<String, Object> result = new LinkedHashMap<>();

        if (tool == null) {
            result.put("error", "Unknown tool: " + toolName);
            return result;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) body.getOrDefault("args", Map.of());
        ToolContext ctx = new ToolContext(client.getWorkdir(), toolName, client.getRunner().getDeps());

        try {
            String output = ToolMiddleware.execute(tool, ctx, args, client.getRunner().getDeps());
            result.put("output", output);
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }
}
