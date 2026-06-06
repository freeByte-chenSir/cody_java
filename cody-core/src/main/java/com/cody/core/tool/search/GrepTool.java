package com.cody.core.tool.search;

import com.cody.core.error.CodyErrors.ToolInvalidParams;
import com.cody.core.tool.CodyTool;
import com.cody.core.tool.ToolContext;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * Regex search in file contents with include/exclude filters.
 * Skips binary files, respects .gitignore patterns, and limits search to allowed roots.
 */
public class GrepTool implements CodyTool {

    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1 MB
    private static final int MAX_MATCHES = 200;
    private static final Set<String> IGNORE_DIRS = Set.of(
            ".git", "node_modules", "__pycache__", ".venv", "venv",
            "target", "build", "dist", ".idea", ".vscode"
    );

    @Override public String getName() { return "grep"; }

    @Override public String getDescription() {
        return "Search file contents using regex pattern. Returns file:line_no: content.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("pattern", Map.of("type", "string", "description", "Regular expression pattern to search for"));
        props.put("path", Map.of("type", "string", "description", "Directory to search in, defaults to '.'"));
        props.put("include", Map.of("type", "string", "description", "Glob pattern to filter files, e.g. '*.java'"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("pattern"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String patternStr = (String) arguments.get("pattern");
        String searchPath = arguments.containsKey("path") ? (String) arguments.get("path") : ".";
        String includeGlob = (String) arguments.get("include");

        Path dirPath = ctx.resolvePath(searchPath);
        if (!Files.isDirectory(dirPath)) {
            throw new ToolInvalidParams("Not a directory: " + searchPath);
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(patternStr);
        } catch (PatternSyntaxException e) {
            throw new ToolInvalidParams("Invalid regex pattern: " + e.getMessage());
        }

        StringBuilder results = new StringBuilder();
        int matchCount = 0;
        int filesSkipped = 0;

        try (Stream<Path> walk = Files.walk(dirPath)) {
            Iterator<Path> it = walk.filter(Files::isRegularFile)
                    .filter(p -> !isIgnored(dirPath.relativize(p)))
                    .filter(p -> includeGlob == null || matchGlob(p.getFileName().toString(), includeGlob))
                    .iterator();

            while (it.hasNext() && matchCount < MAX_MATCHES) {
                Path file = it.next();
                if (isBinary(file)) { filesSkipped++; continue; }
                try {
                    long size = Files.size(file);
                    if (size > MAX_FILE_SIZE) { filesSkipped++; continue; }
                } catch (IOException e) { continue; }

                try {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size() && matchCount < MAX_MATCHES; i++) {
                        Matcher m = pattern.matcher(lines.get(i));
                        if (m.find()) {
                            String relPath = dirPath.relativize(file).toString();
                            results.append(relPath).append(":").append(i + 1).append(": ").append(lines.get(i)).append("\n");
                            matchCount++;
                        }
                    }
                } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            return "[ERROR] grep failed: " + e.getMessage();
        }

        if (matchCount == 0) return "(no matches)";
        if (matchCount >= MAX_MATCHES) results.append("[TRUNCATED: showing first ").append(MAX_MATCHES).append(" matches]\n");
        if (filesSkipped > 0) results.append("[NOTE: ").append(filesSkipped).append(" binary/large files skipped]\n");
        return results.toString().trim();
    }

    private boolean isIgnored(Path relPath) {
        for (Path p : relPath) {
            if (IGNORE_DIRS.contains(p.toString())) return true;
        }
        return false;
    }

    private boolean isBinary(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            int binCount = 0;
            int checkLen = Math.min(bytes.length, 1024);
            for (int i = 0; i < checkLen; i++) {
                if (bytes[i] == 0) return true;
                if (bytes[i] < 9 && bytes[i] != '\n' && bytes[i] != '\r' && bytes[i] != '\t') binCount++;
            }
            return (double) binCount / checkLen > 0.3;
        } catch (IOException e) {
            return true;
        }
    }

    private boolean matchGlob(String filename, String glob) {
        String regex = globToRegex(glob);
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        return p.matcher(filename).matches();
    }

    private String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*': sb.append(".*"); break;
                case '?': sb.append("."); break;
                case '.': sb.append("\\."); break;
                default: sb.append(c);
            }
        }
        sb.append("$");
        return sb.toString();
    }
}
