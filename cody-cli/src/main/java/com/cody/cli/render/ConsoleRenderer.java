package com.cody.cli.render;

import com.cody.core.runner.StreamEvent;

/**
 * Terminal renderer for streaming events.
 *
 * Renders text deltas inline, tool calls as banners, and keeps a clean
 * terminal output during streaming runs.
 */
public class ConsoleRenderer {

    private boolean firstText = true;

    /** Render a single stream event to stdout. */
    public void render(StreamEvent event) {
        switch (event.getType()) {
            case StreamEvent.TEXT_DELTA -> {
                StreamEvent.TextDelta td = (StreamEvent.TextDelta) event;
                if (firstText) {
                    System.out.println();
                    firstText = false;
                }
                System.out.print(td.getContent());
            }
            case StreamEvent.THINKING -> {
                StreamEvent.Thinking t = (StreamEvent.Thinking) event;
                System.out.print("\n[thinking] " + t.getContent());
            }
            case StreamEvent.TOOL_CALL -> {
                StreamEvent.ToolCall tc = (StreamEvent.ToolCall) event;
                System.out.println("\n  [" + tc.getToolName() + "] " + summarizeArgs(tc.getArgs()));
            }
            case StreamEvent.TOOL_RESULT -> {
                StreamEvent.ToolResult tr = (StreamEvent.ToolResult) event;
                String preview = tr.getContent();
                if (preview != null && preview.length() > 120) {
                    preview = preview.substring(0, 120) + "...";
                }
                System.out.println("  -> " + preview);
            }
            case StreamEvent.DONE -> {
                StreamEvent.Done d = (StreamEvent.Done) event;
                System.out.println("\n[Done. " + d.getInputTokens() + " input, "
                        + d.getOutputTokens() + " output tokens]");
            }
            case StreamEvent.ERROR -> {
                StreamEvent.Error e = (StreamEvent.Error) event;
                System.err.println("\n[ERROR] " + e.getMessage());
            }
            case StreamEvent.CIRCUIT_BREAKER -> {
                StreamEvent.CircuitBreaker cb = (StreamEvent.CircuitBreaker) event;
                System.err.println("\n[CIRCUIT BREAKER] " + cb.getReason());
            }
            case StreamEvent.CANCELLED -> {
                System.out.println("\n[Cancelled]");
            }
            case StreamEvent.INTERACTION_REQUEST -> {
                StreamEvent.InteractionRequest ir = (StreamEvent.InteractionRequest) event;
                System.out.println("\n[?] " + ir.getPrompt());
            }
        }
    }

    /** Flush and reset renderer state. */
    public void flush() {
        System.out.flush();
        firstText = true;
    }

    private String summarizeArgs(java.util.Map<String, Object> args) {
        if (args == null || args.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (var entry : args.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            Object val = entry.getValue();
            String valStr = val != null ? val.toString() : "null";
            if (valStr.length() > 40) valStr = valStr.substring(0, 40) + "...";
            sb.append(entry.getKey()).append("=").append(valStr);
        }
        return sb.toString();
    }
}
