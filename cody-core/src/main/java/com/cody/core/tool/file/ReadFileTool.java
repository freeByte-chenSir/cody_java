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

/**
 * Read file contents with optional offset/limit for paginated reading.
 * Supports reading specific line ranges from text files within allowed roots.
 */
public class ReadFileTool implements CodyTool {

    @Override public String getName() { return "read_file"; }

    @Override public String getDescription() {
        return "Read file contents. Use offset and limit for large files.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string", "description", "Path to the file to read"));
        props.put("offset", Map.of("type", "integer", "description", "Line number to start reading from, 0-based, default 0"));
        props.put("limit", Map.of("type", "integer", "description", "Maximum number of lines to return, 0=all, default 0"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("path"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String pathStr = (String) arguments.get("path");
        int offset = arguments.containsKey("offset") ? ((Number) arguments.get("offset")).intValue() : 0;
        int limit = arguments.containsKey("limit") ? ((Number) arguments.get("limit")).intValue() : 0;

        Path filePath = ctx.resolvePath(pathStr);

        if (!Files.exists(filePath)) {
            throw new ToolInvalidParams("File not found: " + pathStr);
        }
        if (Files.isDirectory(filePath)) {
            throw new ToolInvalidParams("Path is a directory: " + pathStr);
        }

        try {
            String content = Files.readString(filePath);
            if (content.length() > 1048576) { // 1 MB limit
                return "[NOTE] File is too large (" + content.length() + " chars). Use offset/limit.";
            }

            if (offset == 0 && limit == 0) {
                return content;
            }

            String[] lines = content.split("\n", -1);
            int end = limit > 0 ? Math.min(offset + limit, lines.length) : lines.length;
            if (offset >= lines.length) {
                return "[Showing lines " + offset + "-" + end + " of " + lines.length + " total]\n";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = offset; i < end; i++) {
                sb.append(lines[i]).append("\n");
            }
            sb.append("[Showing lines ").append(offset).append("-").append(end)
                    .append(" of ").append(lines.length).append(" total]");
            return sb.toString();
        } catch (IOException e) {
            throw new ToolInvalidParams("Cannot read file: " + e.getMessage());
        }
    }
}
