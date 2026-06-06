package com.cody.core.tool.search;

import com.cody.core.tool.CodyTool;
import com.cody.core.tool.ToolContext;

import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Fuzzy file name search by substring matching within allowed roots. */
public class SearchFilesTool implements CodyTool {

    private static final int MAX_RESULTS = 100;

    @Override public String getName() { return "search_files"; }

    @Override public String getDescription() {
        return "Search for files by name (fuzzy match). Returns scored results.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("query", Map.of("type", "string", "description", "Search query to match against file names"));
        props.put("path", Map.of("type", "string", "description", "Directory to search in, defaults to '.'"));
        return props;
    }

    @Override public List<String> getRequiredParameters() { return List.of("query"); }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> arguments) {
        String query = ((String) arguments.get("query")).toLowerCase();
        String searchPath = arguments.containsKey("path") ? (String) arguments.get("path") : ".";
        Path dirPath = ctx.resolvePath(searchPath);

        StringBuilder sb = new StringBuilder();
        try (Stream<Path> walk = Files.walk(dirPath, 10)) {
            walk.filter(Files::isRegularFile)
                    .limit(MAX_RESULTS * 3) // oversample for scoring
                    .map(p -> dirPath.relativize(p))
                    .map(p -> new ScoredFile(p, score(p.getFileName().toString().toLowerCase(), query)))
                    .filter(sf -> sf.score > 0)
                    .sorted((a, b) -> Double.compare(b.score, a.score))
                    .limit(MAX_RESULTS)
                    .forEach(sf -> sb.append("[score:").append(String.format("%.1f", sf.score))
                            .append("] ").append(sf.path).append("\n"));
        } catch (IOException e) {
            return "[ERROR] search_files failed: " + e.getMessage();
        }

        if (sb.length() == 0) return "(no matches)";
        return sb.toString().trim();
    }

    private double score(String filename, String query) {
        if (filename.equals(query)) return 1.0;
        if (filename.startsWith(query)) return 0.8;
        if (filename.contains(query)) return 0.6;
        // Check each query word
        double wordScore = 0;
        for (String word : query.split("[-_.\\s]")) {
            if (filename.contains(word)) wordScore += 0.3;
        }
        return Math.min(wordScore, 0.5);
    }

    private static class ScoredFile {
        final Path path;
        final double score;
        ScoredFile(Path p, double s) { path = p; score = s; }
    }
}
