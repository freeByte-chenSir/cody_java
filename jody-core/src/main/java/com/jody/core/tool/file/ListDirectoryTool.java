package com.jody.core.tool.file;

import com.jody.core.error.JodyErrors.ToolInvalidParams;
import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** List directory contents with optional depth and file filtering within allowed roots. */
public class ListDirectoryTool implements JodyTool {

    @Override public String getName() { return "list_directory"; }

    @Override public String getDescription() {
        return "List the contents of a directory. Defaults to current working directory.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string", "description", "Directory path, defaults to '.'"));
        return props;
    }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String pathStr = arguments.containsKey("path") ? (String) arguments.get("path") : ".";
        Path dirPath = ctx.resolvePath(pathStr);

        if (!Files.exists(dirPath)) {
            throw new ToolInvalidParams("Directory not found: " + pathStr);
        }
        if (!Files.isDirectory(dirPath)) {
            throw new ToolInvalidParams("Not a directory: " + pathStr);
        }

        StringBuilder sb = new StringBuilder();
        try (Stream<Path> entries = Files.list(dirPath)) {
            entries.sorted().limit(500).forEach(p -> {
                String prefix = Files.isDirectory(p) ? "[dir] " : "[file] ";
                sb.append(prefix).append(p.getFileName()).append("\n");
            });
        } catch (IOException e) {
            return "[ERROR] Cannot list directory: " + e.getMessage();
        }

        if (sb.length() == 0) return "(empty)";
        return sb.toString().trim();
    }
}
