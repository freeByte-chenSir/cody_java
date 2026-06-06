package com.cody.core.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Context manager .
 *
 * Two-phase context reduction for keeping LLM context windows within token limits:
 * <ol>
 *   <li><b>Phase 1 — Pruning</b>: replace large tool outputs with markers</li>
 *   <li><b>Phase 2 — Compaction</b>: summarise old messages (LLM-assisted; not in stub)</li>
 * </ol>
 *
 * <p>Also provides {@link #chunkFile(Path, int, int)} for splitting large files into
 * overlapping chunks, and {@link #selectRelevantContext(String, List, int)} for
 * keyword-based relevance scoring.
 */
public class ContextManager {

    /** Default threshold (characters) above which a tool output is pruned. */
    private static final int DEFAULT_PRUNE_THRESHOLD = 60_000;

    private final int pruneThreshold;

    public ContextManager() {
        this(DEFAULT_PRUNE_THRESHOLD);
    }

    public ContextManager(int pruneThreshold) {
        this.pruneThreshold = pruneThreshold;
    }

    // ── Phase 1: Output Pruning ───────────────────────────────────────────

    /**
     * Prune large tool outputs from a message list.
     *
     * <p>Each message is expected to be a map with at least a {@code role} key.
     * Messages with {@code role = "tool"} and a {@code content} value exceeding
     * the prune threshold are replaced with a {@code "[output pruned at <timestamp>]"} marker.
     *
     * @param messages list of message maps
     * @return the same list (mutated in place), with large tool outputs pruned
     */
    public List<Map<String, Object>> pruneToolOutputs(List<Map<String, Object>> messages) {
        if (messages == null) return List.of();

        for (Map<String, Object> msg : messages) {
            if (!"tool".equals(msg.get("role"))) continue;

            Object content = msg.get("content");
            if (!(content instanceof String)) continue;

            String text = (String) content;
            if (text.length() > pruneThreshold) {
                String marker = "[output pruned at " + Instant.now().toString() + "]";
                msg.put("content", marker);
            }
        }
        return messages;
    }

    // ── Phase 2: Message Compaction ───────────────────────────────────────

    /**
     * Compact old messages to fit within a token budget.
     *
     * <p><b>TODO:</b> Implement LLM-assisted summarisation of messages beyond
     * the {@code keepRecent} window.
     *
     * @param messages  the full message list
     * @param maxTokens target maximum token count
     * @return compacted message list (stub returns the original list unchanged)
     */
    public List<Map<String, Object>> compactMessages(List<Map<String, Object>> messages, int maxTokens) {
        // TODO: Implement proper summarisation via LLM or truncation
        // Stub: return messages unchanged
        return messages;
    }

    // ── File Chunking ─────────────────────────────────────────────────────

    /**
     * Split a large file into overlapping chunks of lines.
     *
     * <p>Useful for processing files that exceed the model context window:
     * read the file in chunks with configurable overlap to avoid missing context
     * across chunk boundaries.
     *
     * @param filePath  path to the file
     * @param chunkSize number of lines per chunk
     * @param overlap   number of overlapping lines between consecutive chunks
     * @return list of chunk strings, or empty list if file cannot be read
     */
    public List<String> chunkFile(Path filePath, int chunkSize, int overlap) {
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize must be positive");
        if (overlap < 0) throw new IllegalArgumentException("overlap must be non-negative");
        if (overlap >= chunkSize) throw new IllegalArgumentException("overlap must be less than chunkSize");

        try {
            List<String> allLines = Files.readAllLines(filePath);
            return chunkLines(allLines, chunkSize, overlap, filePath.getFileName().toString());
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Split a list of lines into overlapping chunks.
     *
     * @param lines     all lines from the file
     * @param chunkSize lines per chunk
     * @param overlap   overlapping lines between chunks
     * @param fileName  file name for chunk headers
     * @return list of chunk strings
     */
    public List<String> chunkLines(List<String> lines, int chunkSize, int overlap, String fileName) {
        if (lines == null || lines.isEmpty()) return List.of();

        int totalLines = lines.size();
        int step = chunkSize - overlap;
        if (step <= 0) step = 1;

        List<String> chunks = new ArrayList<>();

        for (int start = 0; start < totalLines; start += step) {
            int end = Math.min(start + chunkSize, totalLines);
            List<String> chunkLines = lines.subList(start, end);

            StringBuilder sb = new StringBuilder();
            sb.append("=== ").append(fileName)
                    .append(" lines ").append(start + 1).append("-").append(end)
                    .append(" of ").append(totalLines).append(" ===\n");

            for (int i = start; i < end; i++) {
                sb.append(lines.get(i)).append("\n");
            }

            chunks.add(sb.toString());

            if (end >= totalLines) break;
        }

        return chunks;
    }

    /**
     * Convenience overload — splits file and returns chunks.
     */
    public List<String> chunkFile(Path filePath, int chunkSize) {
        return chunkFile(filePath, chunkSize, Math.max(1, chunkSize / 10));
    }

    // ── Relevance Scoring ─────────────────────────────────────────────────

    /**
     * Select the most relevant files for a query using simple keyword scoring.
     *
     * <p>Splits the query into words, checks how many of those words appear in
     * each file's name or path, and returns the top files sorted by score until
     * the estimated token budget is exhausted.
     *
     * @param query     the search query / task description
     * @param files     list of file paths to score
     * @param maxTokens approximate token budget
     * @return list of selected file paths (sorted by relevance)
     */
    public List<Path> selectRelevantContext(String query, List<Path> files, int maxTokens) {
        if (query == null || files == null || files.isEmpty()) return List.of();

        // Tokenize query into keywords (lowercase, min 2 chars)
        Set<String> keywords = Arrays.stream(query.toLowerCase().split("\\W+"))
                .filter(w -> w.length() >= 2)
                .collect(Collectors.toSet());

        if (keywords.isEmpty()) return files;

        // Score each file
        Map<Path, Integer> scores = new LinkedHashMap<>();
        for (Path file : files) {
            String pathStr = file.toString().toLowerCase();
            int score = 0;
            for (String kw : keywords) {
                if (pathStr.contains(kw)) score++;
            }
            if (score > 0) scores.put(file, score);
        }

        // Sort by score descending
        List<Path> result = new ArrayList<>();
        int estimatedTokens = 0;
        int tokensPerFile = 500; // rough heuristic

        for (Map.Entry<Path, Integer> entry :
                scores.entrySet().stream()
                        .sorted(Map.Entry.<Path, Integer>comparingByValue().reversed())
                        .collect(Collectors.toList())) {

            estimatedTokens += tokensPerFile;
            if (estimatedTokens > maxTokens && !result.isEmpty()) break;

            result.add(entry.getKey());
        }

        return result;
    }
}
