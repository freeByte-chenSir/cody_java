package com.cody.core.history_impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-level undo/redo history using snapshot storage.
 *
 */
public class FileHistory {

    private final int maxEntriesPerFile;
    private final Map<Path, Deque<Entry>> fileEntries = new ConcurrentHashMap<>();

    public FileHistory(int maxEntriesPerFile) {
        this.maxEntriesPerFile = maxEntriesPerFile;
    }

    /** Record a file change for potential undo. */
    public void record(Path filePath, String contentBefore, String contentAfter) {
        Deque<Entry> entries = fileEntries.computeIfAbsent(filePath.toAbsolutePath(), k -> new ArrayDeque<>());
        // Clear redo stack when new action is taken
        entries.removeIf(e -> e.state == EntryState.REDO);
        entries.addLast(new Entry(contentBefore, contentAfter, EntryState.UNDO));
        while (entries.size() > maxEntriesPerFile) {
            entries.removeFirst();
        }
    }

    /** Undo last change for a file. Returns previous content, or null if nothing to undo. */
    public String undo(Path filePath) {
        Deque<Entry> entries = fileEntries.get(filePath.toAbsolutePath());
        if (entries == null || entries.isEmpty()) return null;

        Entry last = entries.peekLast();
        if (last == null || last.state != EntryState.UNDO) return null;

        entries.removeLast();
        entries.addLast(new Entry(null, last.contentBefore, EntryState.REDO));
        return last.contentBefore != null ? last.contentBefore : last.contentAfter;
    }

    /** Redo last undone change. Returns redone content, or null if nothing to redo. */
    public String redo(Path filePath) {
        Deque<Entry> entries = fileEntries.get(filePath.toAbsolutePath());
        if (entries == null || entries.isEmpty()) return null;

        Entry last = entries.peekLast();
        if (last == null || last.state != EntryState.REDO) return null;

        entries.removeLast();
        entries.addLast(new Entry(null, last.contentAfter, EntryState.UNDO));
        return last.contentAfter;
    }

    /** List all changes recorded for a file. */
    public List<Map<String, Object>> listChanges(Path filePath) {
        List<Map<String, Object>> result = new ArrayList<>();
        Deque<Entry> entries = fileEntries.get(filePath.toAbsolutePath());
        if (entries == null) return result;

        int index = 0;
        for (Entry e : entries) {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("index", index++);
            rec.put("state", e.state.name());
            rec.put("before_preview", truncate(e.contentBefore, 80));
            rec.put("after_preview", truncate(e.contentAfter, 80));
            result.add(rec);
        }
        return result;
    }

    /** Restore file on disk to the undone state. */
    public void restoreFile(Path filePath, String content) throws IOException {
        Files.writeString(filePath, content);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }

    // ── Inner Types ──────────────────────────────────────────────────────

    private enum EntryState { UNDO, REDO }

    private static class Entry {
        final String contentBefore;
        final String contentAfter;
        final EntryState state;

        Entry(String before, String after, EntryState state) {
            this.contentBefore = before;
            this.contentAfter = after;
            this.state = state;
        }
    }
}
