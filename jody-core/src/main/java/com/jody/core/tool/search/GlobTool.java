package com.jody.core.tool.search;

import com.jody.core.tool.JodyTool;
import com.jody.core.tool.ToolContext;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** File name matching by glob pattern. Returns relative paths sorted by modification time. */
public class GlobTool implements JodyTool {

    private static final int MAX_RESULTS = 500;

    @Override public String getName() { return "glob"; }

    @Override public String getDescription() {
        return "Find files matching a glob pattern. Returns relative file paths.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("pattern", Map.of("type", "string", "description", "Glob pattern, e.g. '**/*.java'"));
        props.put("path", Map.of("type", "string", "description", "Directory to search in, defaults to '.'"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("pattern"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String pattern = (String) arguments.get("pattern");
        String searchPath = arguments.containsKey("path") ? (String) arguments.get("path") : ".";
        Path dirPath = ctx.resolvePath(searchPath);

        if (pattern.startsWith("/")) {
            return "[ERROR] Absolute glob patterns are not supported";
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        StringBuilder sb = new StringBuilder();
        try (Stream<Path> walk = Files.walk(dirPath)) {
            walk.filter(p -> matcher.matches(dirPath.relativize(p)))
                    .limit(MAX_RESULTS)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(p -> {
                        String prefix = Files.isDirectory(p) ? "[dir] " : "[file] ";
                        sb.append(prefix).append(dirPath.relativize(p)).append("\n");
                    });
        } catch (IOException e) {
            return "[ERROR] glob failed: " + e.getMessage();
        }

        if (sb.length() == 0) return "(no matches)";
        return sb.toString().trim();
    }
}
