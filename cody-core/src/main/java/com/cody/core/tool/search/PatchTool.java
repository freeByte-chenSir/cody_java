package com.cody.core.tool.search;

import com.cody.core.error.CodyErrors.ToolInvalidParams;
import com.cody.core.tool.CodyTool;
import com.cody.core.tool.ToolContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Apply a unified diff patch to an existing file within allowed roots. */
public class PatchTool implements CodyTool {

    @Override public String getName() { return "patch"; }

    @Override public String getDescription() {
        return "Apply a unified diff patch to a file.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string", "description", "Path to the file to patch"));
        props.put("diff", Map.of("type", "string", "description", "Unified diff to apply"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("path", "diff"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String pathStr = (String) arguments.get("path");
        String diff = (String) arguments.get("diff");
        Path filePath = ctx.resolvePath(pathStr);

        if (!Files.exists(filePath)) {
            throw new ToolInvalidParams("File not found: " + pathStr);
        }

        try {
            String content = Files.readString(filePath);
            String patched = applyUnifiedDiff(content, diff);
            if (patched == null) {
                throw new ToolInvalidParams("Failed to apply patch: hunk context mismatch");
            }
            Files.writeString(filePath, patched);
            return "Patched " + pathStr + " successfully";
        } catch (IOException e) {
            return "[ERROR] Cannot patch file: " + e.getMessage();
        }
    }

    /** Simple unified diff application. Handles basic hunks only. */
    private String applyUnifiedDiff(String original, String diff) {
        StringBuilder result = new StringBuilder(original);
        String[] lines = diff.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("@@ ")) {
                // Parse hunk header: @@ -oldStart,oldCount +newStart,newCount @@
                // For simplicity, we find the context line after the header
                StringBuilder oldBlock = new StringBuilder();
                StringBuilder newBlock = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].startsWith("@@ ")) {
                    String hunkLine = lines[i];
                    if (hunkLine.startsWith("-")) {
                        oldBlock.append(hunkLine.substring(1)).append("\n");
                    } else if (hunkLine.startsWith("+")) {
                        newBlock.append(hunkLine.substring(1)).append("\n");
                    } else {
                        // Context line (starts with space)
                        String ctxLine = hunkLine.startsWith(" ") ? hunkLine.substring(1) : hunkLine;
                        oldBlock.append(ctxLine).append("\n");
                        newBlock.append(ctxLine).append("\n");
                    }
                    i++;
                }
                i--; // Back up, outer loop will advance

                int idx = result.indexOf(oldBlock.toString().trim());
                if (idx == -1) return null;
                // Replace: remove old block, insert new block
                result.replace(idx, idx + oldBlock.length(), newBlock.toString());
            }
        }
        return result.toString();
    }
}
