package com.jody.sdk;

import com.jody.core.config.Config;
import com.jody.core.runner.AgentRunner;
import com.jody.core.runner.StreamEvent;
import com.jody.sdk.config.SdkConfig;
import com.jody.sdk.events.EventManager;
import com.jody.sdk.types.StreamChunk;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;

/**
 * Main SDK clientAsyncJodyClient.
 *
 * Directly wraps AgentRunner for in-process AI agent access.
 * Supports run(), stream(), and event-based consumption.
 */
public class JodyClient {

    private final Config config;
    private final Path workdir;
    private final AgentRunner runner;
    private final SdkConfig sdkConfig;
    private final EventManager events;

    JodyClient(Config config, Path workdir, SdkConfig sdkConfig) {
        this.config = config;
        this.workdir = workdir;
        this.sdkConfig = sdkConfig;
        this.runner = new AgentRunner(config, workdir);
        this.events = new EventManager();
    }

    /** Synchronous run. */
    public AgentRunner.RunResult run(String prompt) {
        return runner.run(prompt, null,
                sdkConfig.getIncludeTools() != null ? Set.copyOf(sdkConfig.getIncludeTools()) : null,
                sdkConfig.getExcludeTools() != null ? Set.copyOf(sdkConfig.getExcludeTools()) : null,
                null);
    }

    /** Run with session ID. */
    public AgentRunner.RunResult run(String prompt, String sessionId) {
        return runner.run(prompt, sessionId,
                sdkConfig.getIncludeTools() != null ? Set.copyOf(sdkConfig.getIncludeTools()) : null,
                sdkConfig.getExcludeTools() != null ? Set.copyOf(sdkConfig.getExcludeTools()) : null,
                null);
    }

    /** Streaming run. Consume events from the returned iterator. */
    public Iterator<StreamEvent> stream(String prompt) {
        return runner.stream(prompt);
    }

    /** Streaming run with event manager. Events are dispatched to registered listeners. */
    public void streamWithEvents(String prompt) {
        Iterator<StreamEvent> iter = runner.stream(prompt);
        while (iter.hasNext()) {
            StreamEvent event = iter.next();
            if (event == StreamEvent.POISON_PILL) break;

            switch (event.getType()) {
                case StreamEvent.TEXT_DELTA -> {
                    StreamEvent.TextDelta td = (StreamEvent.TextDelta) event;
                    events.emitText(td.getContent());
                }
                case StreamEvent.TOOL_CALL -> {
                    StreamEvent.ToolCall tc = (StreamEvent.ToolCall) event;
                    events.emitRaw("[tool_call] " + tc.getToolName());
                }
                case StreamEvent.TOOL_RESULT -> {
                    StreamEvent.ToolResult tr = (StreamEvent.ToolResult) event;
                    events.emitRaw("[tool_result] " + tr.getToolName() + ": " + tr.getContent());
                }
                case StreamEvent.DONE -> {
                    events.emitDone();
                }
                case StreamEvent.ERROR -> {
                    StreamEvent.Error err = (StreamEvent.Error) event;
                    events.emitError(err.getMessage());
                }
                case StreamEvent.CIRCUIT_BREAKER -> {
                    StreamEvent.CircuitBreaker cb = (StreamEvent.CircuitBreaker) event;
                    events.emitError("Circuit breaker: " + cb.getReason());
                }
                case StreamEvent.CANCELLED -> {
                    events.emitError("Run cancelled");
                }
            }
        }
    }

    // ── Accessors ──────────────────────────────────────────────────

    public Config getConfig() { return config; }
    public Path getWorkdir() { return workdir; }
    public AgentRunner getRunner() { return runner; }
    public EventManager getEvents() { return events; }
}
