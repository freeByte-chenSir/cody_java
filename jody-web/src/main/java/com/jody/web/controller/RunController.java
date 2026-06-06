package com.jody.web.controller;

import com.jody.core.runner.AgentRunner;
import com.jody.core.runner.StreamEvent;
import com.jody.sdk.JodyClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * Run endpoints.
 *
 * POST /run         — synchronous execution
 * POST /run/stream  — SSE streaming execution
 */
@RestController
@RequestMapping("/run")
public class RunController {

    private final JodyClient client;

    public RunController(JodyClient client) {
        this.client = client;
    }

    /** Synchronous run. */
    @PostMapping
    public Map<String, Object> run(@RequestBody Map<String, Object> body) {
        String prompt = (String) body.getOrDefault("prompt", "");
        String sessionId = (String) body.get("session_id");

        AgentRunner.RunResult result = client.run(prompt);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("output", result.getOutput());
        response.put("usage", Map.of(
                "input_tokens", result.getUsage().getInputTokens(),
                "output_tokens", result.getUsage().getOutputTokens(),
                "total_tokens", result.getUsage().getTotalTokens()
        ));
        if (sessionId != null) response.put("session_id", sessionId);
        return response;
    }

    /** SSE streaming run. */
    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> runStream(@RequestBody Map<String, Object> body) {
        String prompt = (String) body.getOrDefault("prompt", "");

        return Flux.create(sink -> {
            Thread producer = new Thread(() -> {
                try {
                    Iterator<StreamEvent> events = client.stream(prompt);
                    while (events.hasNext()) {
                        StreamEvent ev = events.next();
                        if (ev == StreamEvent.POISON_PILL) break;

                        Map<String, Object> sseEvent = new LinkedHashMap<>();
                        sseEvent.put("type", ev.getType());

                        switch (ev.getType()) {
                            case StreamEvent.TEXT_DELTA ->
                                    sseEvent.put("content", ((StreamEvent.TextDelta) ev).getContent());
                            case StreamEvent.THINKING ->
                                    sseEvent.put("content", ((StreamEvent.Thinking) ev).getContent());
                            case StreamEvent.TOOL_CALL -> {
                                StreamEvent.ToolCall tc = (StreamEvent.ToolCall) ev;
                                sseEvent.put("tool_name", tc.getToolName());
                                sseEvent.put("args", tc.getArgs());
                                sseEvent.put("call_id", tc.getToolCallId());
                            }
                            case StreamEvent.TOOL_RESULT -> {
                                StreamEvent.ToolResult tr = (StreamEvent.ToolResult) ev;
                                sseEvent.put("tool_name", tr.getToolName());
                                sseEvent.put("content", tr.getContent());
                            }
                            case StreamEvent.DONE -> {
                                StreamEvent.Done d = (StreamEvent.Done) ev;
                                sseEvent.put("output", d.getOutput());
                                sseEvent.put("input_tokens", d.getInputTokens());
                                sseEvent.put("output_tokens", d.getOutputTokens());
                            }
                            case StreamEvent.ERROR ->
                                    sseEvent.put("message", ((StreamEvent.Error) ev).getMessage());
                            case StreamEvent.CIRCUIT_BREAKER -> {
                                StreamEvent.CircuitBreaker cb = (StreamEvent.CircuitBreaker) ev;
                                sseEvent.put("reason", cb.getReason());
                            }
                        }
                        sink.next(sseEvent);
                    }
                } catch (Exception e) {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("type", "error");
                    err.put("message", e.getMessage());
                    sink.next(err);
                }
                sink.complete();
            }, "web-stream-producer");
            producer.setDaemon(true);
            producer.start();
        });
    }
}
