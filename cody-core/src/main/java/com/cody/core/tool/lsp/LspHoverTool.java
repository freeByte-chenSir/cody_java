package com.cody.core.tool.lsp;

import com.cody.core.deps.CodyDeps;
import com.cody.core.tool.CodyTool;
import com.cody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hover information via LSP.
 *
 */
public class LspHoverTool implements CodyTool {

    @Override public String getName() { return "lsp_hover"; }

    @Override public String getDescription() {
        return "Get hover information (type, docs) for a symbol at the given file position via LSP.";
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
        CodyDeps deps = ctx.getDeps(CodyDeps.class);
        Object lspClient = deps != null ? deps.getLspClient() : null;

        if (lspClient == null) {
            return "[LSP_NOT_AVAILABLE] LSP client is not configured.";
        }

        try {
            Object result = lspClient.getClass()
                    .getMethod("getHover", String.class, int.class, int.class)
                    .invoke(lspClient, filePath, line, column);
            if (result == null) {
                return "[NO_HOVER_INFO] No hover information at " + filePath + ":" + line + ":" + column;
            }
            return result.toString();
        } catch (Exception e) {
            return "[LSP_NOT_AVAILABLE] Cannot get hover info for '" + filePath + "': "
                    + e.getMessage();
        }
    }
}
