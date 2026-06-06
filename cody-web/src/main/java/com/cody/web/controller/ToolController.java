package com.cody.web.controller;

import com.cody.core.tool.CodyTool;
import com.cody.core.tool.ToolContext;
import com.cody.core.tool.ToolMiddleware;
import com.cody.core.tool.ToolRegistry;
import com.cody.sdk.CodyClient;
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

    private final CodyClient client;

    public ToolController(CodyClient client) {
        this.client = client;
    }

    /** List all registered tools. */
    @GetMapping
    public List<Map<String, Object>> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (CodyTool tool : ToolRegistry.CORE_TOOLS) {
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
        CodyTool tool = ToolRegistry.getTool(toolName);
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
