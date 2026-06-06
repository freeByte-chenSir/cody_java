package com.jody.core.runner;

import java.util.List;
import java.util.Map;

/**
 * Discriminated union of stream event types produced during agent execution.
 *
 * Each event type is a nested static class with a type discriminator.
 * Consumers dispatch using {@code instanceof} checks or the type string.
 * A {@code POISON_PILL} sentinel marks end of stream.
 */
public abstract class StreamEvent {

    public static final String TEXT_DELTA = "text_delta";
    public static final String THINKING = "thinking";
    public static final String TOOL_CALL = "tool_call";
    public static final String TOOL_RESULT = "tool_result";
    public static final String DONE = "done";
    public static final String CANCELLED = "cancelled";
    public static final String CIRCUIT_BREAKER = "circuit_breaker";
    public static final String ERROR = "error";
    public static final String INTERACTION_REQUEST = "interaction_request";
    public static final String USER_INPUT_RECEIVED = "user_input_received";

    /** Sentinel object marking end of stream. */
    public static final StreamEvent POISON_PILL = new StreamEvent() {
        @Override public String getType() { return "__END__"; }
    };

    /** Discriminator. */
    public abstract String getType();

    // ── Event Types ──────────────────────────────────────────────────────

    /** Incremental text output from the model. */
    public static class TextDelta extends StreamEvent {
        private final String content;
        public TextDelta(String content) { this.content = content; }
        public String getContent() { return content; }
        @Override public String getType() { return TEXT_DELTA; }
    }

    /** Incremental thinking content from the model. */
    public static class Thinking extends StreamEvent {
        private final String content;
        public Thinking(String content) { this.content = content; }
        public String getContent() { return content; }
        @Override public String getType() { return THINKING; }
    }

    /** A tool call has been initiated by the model. */
    public static class ToolCall extends StreamEvent {
        private final String toolName;
        private final Map<String, Object> args;
        private final String toolCallId;
        public ToolCall(String toolName, Map<String, Object> args, String toolCallId) {
            this.toolName = toolName; this.args = args; this.toolCallId = toolCallId;
        }
        public String getToolName() { return toolName; }
        public Map<String, Object> getArgs() { return args; }
        public String getToolCallId() { return toolCallId; }
        @Override public String getType() { return TOOL_CALL; }
    }

    /** A tool call has returned a result. */
    public static class ToolResult extends StreamEvent {
        private final String toolName;
        private final String toolCallId;
        private final String content;
        public ToolResult(String toolName, String toolCallId, String content) {
            this.toolName = toolName; this.toolCallId = toolCallId; this.content = content;
        }
        public String getToolName() { return toolName; }
        public String getToolCallId() { return toolCallId; }
        public String getContent() { return content; }
        @Override public String getType() { return TOOL_RESULT; }
    }

    /** Stream complete, contains final result. */
    public static class Done extends StreamEvent {
        private final String output;
        private final long inputTokens;
        private final long outputTokens;
        private final List<AgentRunner.ToolTrace> toolTraces;
        public Done(String output, long inputTokens, long outputTokens, List<AgentRunner.ToolTrace> toolTraces) {
            this.output = output; this.inputTokens = inputTokens; this.outputTokens = outputTokens;
            this.toolTraces = toolTraces;
        }
        public String getOutput() { return output; }
        public long getInputTokens() { return inputTokens; }
        public long getOutputTokens() { return outputTokens; }
        public List<AgentRunner.ToolTrace> getToolTraces() { return toolTraces; }
        @Override public String getType() { return DONE; }
    }

    /** Error occurred. */
    public static class Error extends StreamEvent {
        private final String message;
        public Error(String message) { this.message = message; }
        public String getMessage() { return message; }
        @Override public String getType() { return ERROR; }
    }

    /** Circuit breaker tripped. */
    public static class CircuitBreaker extends StreamEvent {
        private final String reason;
        private final long tokensUsed;
        private final double costUsd;
        public CircuitBreaker(String reason, long tokensUsed, double costUsd) {
            this.reason = reason; this.tokensUsed = tokensUsed; this.costUsd = costUsd;
        }
        public String getReason() { return reason; }
        public long getTokensUsed() { return tokensUsed; }
        public double getCostUsd() { return costUsd; }
        @Override public String getType() { return CIRCUIT_BREAKER; }
    }

    /** Run cancelled by caller. */
    public static class Cancelled extends StreamEvent {
        @Override public String getType() { return CANCELLED; }
    }

    /** Human interaction needed. */
    public static class InteractionRequest extends StreamEvent {
        private final String requestId;
        private final String kind;
        private final String prompt;
        public InteractionRequest(String requestId, String kind, String prompt) {
            this.requestId = requestId; this.kind = kind; this.prompt = prompt;
        }
        public String getRequestId() { return requestId; }
        public String getKind() { return kind; }
        public String getPrompt() { return prompt; }
        @Override public String getType() { return INTERACTION_REQUEST; }
    }
}
