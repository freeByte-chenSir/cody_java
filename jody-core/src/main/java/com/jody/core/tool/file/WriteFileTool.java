package com.jody.core.tool.file;

import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Write or overwrite file contents at the given path within allowed roots. */
public class WriteFileTool implements JodyTool {

    @Override public String getName() { return "write_file"; }

    @Override public String getDescription() {
        return "Write content to a file, creating parent directories if needed. Overwrites existing files.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string", "description", "Path to the file to write"));
        props.put("content", Map.of("type", "string", "description", "Content to write to the file"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("path", "content"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String pathStr = (String) arguments.get("path");
        String content = (String) arguments.get("content");
        Path filePath = ctx.resolvePath(pathStr);

        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);
            return "Written " + content.length() + " bytes to " + pathStr;
        } catch (IOException e) {
            return "[ERROR] Cannot write file: " + e.getMessage();
        }
    }
}
