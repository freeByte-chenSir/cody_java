package com.cody.core.tool.file;

import com.cody.core.error.CodyErrors.ToolInvalidParams;
import com.cody.core.tool.CodyTool;
import com.cody.core.tool.ToolContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Perform exact string replacement in an existing file. Fails if the target text is not unique. */
public class EditFileTool implements CodyTool {

    @Override public String getName() { return "edit_file"; }

    @Override public String getDescription() {
        return "Replace exact text in a file. Provide old_text and new_text.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string", "description", "Path to the file to edit"));
        props.put("old_text", Map.of("type", "string", "description", "Exact text to find and replace"));
        props.put("new_text", Map.of("type", "string", "description", "Replacement text"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("path", "old_text", "new_text"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String pathStr = (String) arguments.get("path");
        String oldText = (String) arguments.get("old_text");
        String newText = (String) arguments.get("new_text");
        Path filePath = ctx.resolvePath(pathStr);

        if (!Files.exists(filePath)) {
            throw new ToolInvalidParams("File not found: " + pathStr);
        }

        try {
            String content = Files.readString(filePath);
            int index = content.indexOf(oldText);
            if (index == -1) {
                throw new ToolInvalidParams("Text not found in " + pathStr + ": \"" + oldText.substring(0, Math.min(100, oldText.length())) + "...\"");
            }

            // Replace only the first occurrence
            String newContent = content.substring(0, index) + newText + content.substring(index + oldText.length());
            Files.writeString(filePath, newContent);
            return "Edited " + pathStr + ": replaced " + oldText.length() + " chars with " + newText.length() + " chars";
        } catch (IOException e) {
            return "[ERROR] Cannot edit file: " + e.getMessage();
        }
    }
}
