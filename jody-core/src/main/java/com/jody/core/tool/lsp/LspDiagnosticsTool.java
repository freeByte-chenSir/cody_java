package com.jody.core.tool.lsp;

import com.jody.core.deps.JodyDeps;
import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gets LSP diagnostics for a file.
 *
 */
public class LspDiagnosticsTool implements JodyTool {

    @Override public String getName() { return "lsp_diagnostics"; }

    @Override public String getDescription() {
        return "Get LSP diagnostics (errors, warnings, hints) for a file.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("file_path", Map.of("type", "string", "description", "Path to the file to get diagnostics for"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("file_path"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String filePath = (String) arguments.get("file_path");
        JodyDeps deps = ctx.getDeps(JodyDeps.class);
        Object lspClient = deps != null ? deps.getLspClient() : null;

        if (lspClient == null) {
            return "[LSP_NOT_AVAILABLE] LSP client is not configured.";
        }

        try {
            Object result = lspClient.getClass()
                    .getMethod("getDiagnostics", String.class)
                    .invoke(lspClient, filePath);
            if (result == null) {
                return "[]"; // no diagnostics
            }
            return result.toString();
        } catch (Exception e) {
            return "[LSP_NOT_AVAILABLE] Cannot get diagnostics for '" + filePath + "': "
                    + e.getMessage();
        }
    }
}
