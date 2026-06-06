package com.jody.core.memory_impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/**
 * Cross-session knowledge store .
 *
 * Persists lessons learned across agent sessions so insights from one task
 * can inform future tasks in the same project.
 *
 * <h3>Storage Layout</h3>
 * <pre>
 * ~/.jody/memory/&lt;project_hash&gt;/
 *   conventions.json
 *   patterns.json
 *   issues.json
 *   decisions.json
 * </pre>
 *
 * <h3>Categories</h3>
 * <ul>
 *   <li><b>conventions</b> — coding conventions, naming, style</li>
 *   <li><b>patterns</b> — design patterns, refactoring patterns</li>
 *   <li><b>issues</b> — known problems, gotchas, workarounds</li>
 *   <li><b>decisions</b> — architecture decisions, trade-offs</li>
 * </ul>
 *
 * <p>Each category holds at most 50 entries. Entries with confidence below 0.3 are
 * excluded from prompt injection.
 */
public class ProjectMemory {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ENTRIES_PER_CATEGORY = 50;
    private static final double PROMPT_CONFIDENCE_THRESHOLD = 0.3;

    private static final List<String> CATEGORIES = List.of(
            "conventions", "patterns", "issues", "decisions"
    );

    private final Path storeDir;
    private final Map<String, List<MemoryEntry>> cache = new LinkedHashMap<>();

    /**
     * Create a ProjectMemory store for the given workdir.
     *
     * @param workdir the project root directory (used to compute the project hash)
     */
    public ProjectMemory(Path workdir) {
        String projectHash = computeProjectHash(workdir);
        this.storeDir = Paths.get(System.getProperty("user.home"), ".jody", "memory", projectHash);
        for (String category : CATEGORIES) {
            cache.put(category, new ArrayList<>());
        }
    }

    /** Compute a stable hash string for the project directory. */
    private static String computeProjectHash(Path workdir) {
        if (workdir == null) return "default";
        String absPath = workdir.toAbsolutePath().normalize().toString();
        int hash = Math.abs(absPath.hashCode());
        return String.format("%08x", hash);
    }

    // ── Load / Persist ────────────────────────────────────────────────────

    /** Load all categories from disk into the in-memory cache. */
    public synchronized void load() {
        try {
            Files.createDirectories(storeDir);
        } catch (IOException ignored) {
            return;
        }

        for (String category : CATEGORIES) {
            Path file = storeDir.resolve(category + ".json");
            if (!Files.isRegularFile(file)) continue;

            try {
                List<MemoryEntry> entries = MAPPER.readValue(
                        file.toFile(), new TypeReference<List<MemoryEntry>>() {});
                cache.put(category, new ArrayList<>(entries));
            } catch (IOException e) {
                cache.put(category, new ArrayList<>());
            }
        }
    }

    /** Persist all categories from the in-memory cache to disk. */
    public synchronized void save() {
        try {
            Files.createDirectories(storeDir);
        } catch (IOException ignored) {
            return;
        }

        for (String category : CATEGORIES) {
            Path file = storeDir.resolve(category + ".json");
            try {
                MAPPER.writerWithDefaultPrettyPrinter()
                        .writeValue(file.toFile(), cache.get(category));
            } catch (IOException ignored) {
                // Best-effort persistence
            }
        }
    }

    // ── Add Entry ─────────────────────────────────────────────────────────

    /**
     * Save a new memory entry into the specified category.
     *
     * @param category      one of: conventions, patterns, issues, decisions
     * @param content       the memory content (free text)
     * @param sourceTaskId  optional ID of the task that produced this insight
     */
    public synchronized void save(String category, String content, String sourceTaskId) {
        if (!CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Unknown category: " + category
                    + ". Valid categories: " + CATEGORIES);
        }

        MemoryEntry entry = new MemoryEntry(content, sourceTaskId);
        List<MemoryEntry> entries = cache.computeIfAbsent(category, k -> new ArrayList<>());
        entries.add(0, entry); // Prepend — newest first

        // Trim to max entries
        while (entries.size() > MAX_ENTRIES_PER_CATEGORY) {
            entries.remove(entries.size() - 1);
        }

        save();
    }

    /** Overload without sourceTaskId. */
    public synchronized void save(String category, String content) {
        save(category, content, null);
    }

    // ── Prompt Injection ──────────────────────────────────────────────────

    /**
     * Format memory entries for injection into the system prompt.
     *
     * <p>Only entries with confidence >= 0.3 are included. The output is a
     * Markdown-formatted block suitable for appending to the system message.
     *
     * @return formatted Markdown string, or empty string if no qualifying entries exist
     */
    public synchronized String getForPrompt() {
        load();

        StringBuilder sb = new StringBuilder();
        boolean hasEntries = false;

        for (String category : CATEGORIES) {
            List<MemoryEntry> entries = cache.getOrDefault(category, List.of());
            List<MemoryEntry> qualified = entries.stream()
                    .filter(e -> e.confidence >= PROMPT_CONFIDENCE_THRESHOLD)
                    .collect(java.util.stream.Collectors.toList());

            if (qualified.isEmpty()) continue;

            if (!hasEntries) {
                sb.append("## Project Memory\n\n");
                hasEntries = true;
            }

            sb.append("### ").append(capitalize(category)).append("\n\n");
            for (MemoryEntry e : qualified) {
                sb.append("- ").append(e.content).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // ── Query ─────────────────────────────────────────────────────────────

    /** Return a read-only snapshot of all categories and their entries. */
    public synchronized Map<String, List<MemoryEntry>> getAll() {
        load();
        Map<String, List<MemoryEntry>> copy = new LinkedHashMap<>();
        for (String category : CATEGORIES) {
            copy.put(category, List.copyOf(cache.getOrDefault(category, List.of())));
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Clear all memory entries in all categories. */
    public synchronized void clear() {
        for (String category : CATEGORIES) {
            cache.put(category, new ArrayList<>());
        }
        save();
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    /** Return the on-disk storage directory. */
    public Path getStoreDir() {
        return storeDir;
    }

    /** Return the number of entries in a category. */
    public synchronized int size(String category) {
        return cache.getOrDefault(category, List.of()).size();
    }

    /** Return the total number of entries across all categories. */
    public synchronized int totalSize() {
        return cache.values().stream().mapToInt(List::size).sum();
    }

    // ── Inner Class: MemoryEntry ──────────────────────────────────────────

    /**
     * A single memory entry. Serialised as JSON by Jackson.
     */
    public static class MemoryEntry {
        private String content;
        private String sourceTaskId;
        private double confidence;
        private String createdAt;

        /** No-arg constructor for Jackson deserialisation. */
        public MemoryEntry() {}

        /** Create an entry with default confidence 1.0 and current timestamp. */
        public MemoryEntry(String content, String sourceTaskId) {
            this.content = content;
            this.sourceTaskId = sourceTaskId;
            this.confidence = 1.0;
            this.createdAt = Instant.now().toString();
        }

        /** Create an entry with explicit confidence. */
        public MemoryEntry(String content, String sourceTaskId, double confidence) {
            this.content = content;
            this.sourceTaskId = sourceTaskId;
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            this.createdAt = Instant.now().toString();
        }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getSourceTaskId() { return sourceTaskId; }
        public void setSourceTaskId(String sourceTaskId) { this.sourceTaskId = sourceTaskId; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) {
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        @Override
        public String toString() {
            return "MemoryEntry{content='" + truncate(content, 60)
                    + "', confidence=" + confidence + ", createdAt=" + createdAt + "}";
        }

        private static String truncate(String s, int maxLen) {
            if (s == null) return "null";
            return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
        }
    }
}
