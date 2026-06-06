package com.jody.core.tool.lsp;

import com.jody.core.deps.JodyDeps;
import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Go-to-definition via LSP.
 *
 */
public class LspDefinitionTool implements JodyTool {

    @Override public String getName() { return "lsp_definition"; }

    @Override public String getDescription() {
        return "Get the definition location of a symbol at the given file position via LSP.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("file_path", Map.of("type", "string", "description", "Path to the source file"));
        props.put("line", Map.of("type", "integer", "description", "Line number (0-based)"));
        props.put("column", Map.of("type", "integer", "description", "Column number (0-based)"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("file_path", "line", "column"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String filePath = (String) arguments.get("file_path");
        int line = ((Number) arguments.get("line")).intValue();
        int column = ((Number) arguments.get("column")).intValue();
        JodyDeps deps = ctx.getDeps(JodyDeps.class);
        Object lspClient = deps != null ? deps.getLspClient() : null;

        if (lspClient == null) {
            return "[LSP_NOT_AVAILABLE] LSP client is not configured.";
        }

        try {
            Object result = lspClient.getClass()
                    .getMethod("getDefinition", String.class, int.class, int.class)
                    .invoke(lspClient, filePath, line, column);
            if (result == null) {
                return "[NO_DEFINITION] No definition found at " + filePath + ":" + line + ":" + column;
            }
            return result.toString();
        } catch (Exception e) {
            return "[LSP_NOT_AVAILABLE] Cannot get definition for '" + filePath + "': "
                    + e.getMessage();
        }
    }
}
