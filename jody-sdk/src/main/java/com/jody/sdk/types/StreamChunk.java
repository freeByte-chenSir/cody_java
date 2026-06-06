package com.jody.sdk.types;

import com.jody.core.runner.AgentRunner;

import java.util.List;
import java.util.Map;

/**
 * Discriminated union of streaming event types for SDK consumers.
 * Wraps internal StreamEvent types for public API consumption.
 *
 */
public abstract class StreamChunk {

    public abstract String getType();

    /** Text delta from the model. */
    public static class TextDelta extends StreamChunk {
        private final String content;
        public TextDelta(String content) { this.content = content; }
        public String getContent() { return content; }
        @Override public String getType() { return "text_delta"; }
    }

    /** A tool call was made. */
    public static class ToolCall extends StreamChunk {
        private final String toolName;
        private final Map<String, Object> args;
        private final String callId;
        public ToolCall(String toolName, Map<String, Object> args, String callId) {
            this.toolName = toolName; this.args = args; this.callId = callId;
        }
        public String getToolName() { return toolName; }
        public Map<String, Object> getArgs() { return args; }
        public String getCallId() { return callId; }
        @Override public String getType() { return "tool_call"; }
    }

    /** A tool call result. */
    public static class ToolResult extends StreamChunk {
        private final String toolName;
        private final String callId;
        private final String content;
        public ToolResult(String toolName, String callId, String content) {
            this.toolName = toolName; this.callId = callId; this.content = content;
        }
        public String getToolName() { return toolName; }
        public String getCallId() { return callId; }
        public String getContent() { return content; }
        @Override public String getType() { return "tool_result"; }
    }

    /** Run completed. */
    public static class Done extends StreamChunk {
        private final String output;
        private final long inputTokens;
        private final long outputTokens;
        private final List<AgentRunner.ToolTrace> traces;
        public Done(String output, long inputTokens, long outputTokens, List<AgentRunner.ToolTrace> traces) {
            this.output = output; this.inputTokens = inputTokens; this.outputTokens = outputTokens; this.traces = traces;
        }
        public String getOutput() { return output; }
        public long getInputTokens() { return inputTokens; }
        public long getOutputTokens() { return outputTokens; }
        public List<AgentRunner.ToolTrace> getTraces() { return traces; }
        @Override public String getType() { return "done"; }
    }

    /** Error occurred. */
    public static class Error extends StreamChunk {
        private final String message;
        public Error(String message) { this.message = message; }
        public String getMessage() { return message; }
        @Override public String getType() { return "error"; }
    }

    /** Cancelled. */
    public static class Cancelled extends StreamChunk {
        @Override public String getType() { return "cancelled"; }
    }
}
