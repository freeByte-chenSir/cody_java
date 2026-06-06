package com.cody.web.controller;

import com.cody.core.session.SessionStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.*;

/**
 * Session CRUD endpoints.
 *
 * GET    /sessions          — list sessions
 * GET    /sessions/{id}     — get session details
 * POST   /sessions          — create session
 * PATCH  /sessions/{id}     — update session
 * DELETE /sessions/{id}     — delete session
 */
@RestController
@RequestMapping("/sessions")
public class SessionController {

    private final SessionStore store;

    public SessionController(SessionStore store) {
        this.store = store;
    }

    @GetMapping
    public List<Map<String, Object>> listSessions(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return store.listSessions(limit, offset);
    }

    @GetMapping("/{id}")
    public Map<String, Object> getSession(@PathVariable String id) {
        Map<String, Object> session = store.getSession(id);
        if (session == null) throw new ResponseStatusException(404, "Session not found: " + id, null);
        return session;
    }

    @PostMapping
    public Map<String, Object> createSession(@RequestBody Map<String, Object> body) {
        String id = (String) body.getOrDefault("id", UUID.randomUUID().toString());
        String title = (String) body.getOrDefault("title", "Untitled");
        String model = (String) body.getOrDefault("model", "claude-sonnet-4-0");
        return store.createSession(id, title, model, Path.of("."));
    }

    @PatchMapping("/{id}")
    public Map<String, Object> updateSession(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        String status = (String) body.get("status");
        store.updateSession(id, title, status);
        Map<String, Object> session = store.getSession(id);
        if (session == null) throw new ResponseStatusException(404, "Session not found: " + id, null);
        return session;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteSession(@PathVariable String id) {
        store.deleteSession(id);
        return Map.of("deleted", id);
    }
}
