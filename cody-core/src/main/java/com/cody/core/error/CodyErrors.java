package com.cody.core.error;

/**
 * Exception hierarchy .
 *
 * Tool-layer exceptions (all extend ToolError):
 *   ToolError (base) → ToolPermissionDenied, ToolPathDenied, ToolInvalidParams
 *
 * Server-layer exceptions:
 *   CodyApiError (base) → various subtypes
 *
 * Other exceptions:
 *   CircuitBreakerError, InteractionTimeoutError
 */
public class CodyErrors {

    /** Standard error codes with HTTP status mapping. */
    public enum ErrorCode {
        INVALID_PARAMS(400),
        AUTH_FAILED(401),
        PERMISSION_DENIED(403),
        NOT_FOUND(404),
        SESSION_NOT_FOUND(404),
        SKILL_NOT_FOUND(404),
        TOOL_NOT_FOUND(404),
        AGENT_NOT_FOUND(404),
        RATE_LIMITED(429),
        TIMEOUT(408),
        TOOL_ERROR(500),
        MODEL_ERROR(500),
        AGENT_ERROR(500),
        AGENT_LIMIT_REACHED(500),
        SERVER_ERROR(500),
        MCP_ERROR(500);

        private final int httpStatus;

        ErrorCode(int httpStatus) {
            this.httpStatus = httpStatus;
        }

        public int httpStatus() {
            return httpStatus;
        }
    }

    // ── Tool-layer exceptions ──────────────────────────────────────────

    /** Base class for tool-layer exceptions. Caught by ToolMiddleware, converted to ModelRetry. */
    public static class ToolError extends RuntimeException {
        private final ErrorCode code;

        public ToolError(ErrorCode code, String message) {
            super(message);
            this.code = code;
        }

        public ToolError(ErrorCode code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public ErrorCode getCode() {
            return code;
        }
    }

    /** Raised when a tool is denied by the permission manager. */
    public static class ToolPermissionDenied extends ToolError {
        public ToolPermissionDenied(String toolName) {
            super(ErrorCode.PERMISSION_DENIED, "Permission denied for tool: " + toolName);
        }

        public ToolPermissionDenied(String message, String toolName) {
            super(ErrorCode.PERMISSION_DENIED, message);
        }
    }

    /** Raised when a file path is outside allowed roots. */
    public static class ToolPathDenied extends ToolError {
        public ToolPathDenied(String path, String workdir) {
            super(ErrorCode.PERMISSION_DENIED,
                    "Path '" + path + "' is outside the working directory '" + workdir + "'");
        }
    }

    /** Raised for invalid tool parameters (bad regex, file not found, etc.). */
    public static class ToolInvalidParams extends ToolError {
        public ToolInvalidParams(String message) {
            super(ErrorCode.INVALID_PARAMS, message);
        }
    }

    // ── Server-layer exceptions ─────────────────────────────────────────

    /** Base class for HTTP API errors. */
    public static class CodyApiError extends RuntimeException {
        private final ErrorCode code;

        public CodyApiError(ErrorCode code, String message) {
            super(message);
            this.code = code;
        }

        public ErrorCode getCode() {
            return code;
        }

        public int getHttpStatus() {
            return code.httpStatus();
        }
    }

    public static class AuthFailedError extends CodyApiError {
        public AuthFailedError(String message) {
            super(ErrorCode.AUTH_FAILED, message);
        }
    }

    public static class NotFoundError extends CodyApiError {
        public NotFoundError(ErrorCode code, String message) {
            super(code, message);
        }
    }

    public static class RateLimitError extends CodyApiError {
        private final long retryAfterSeconds;

        public RateLimitError(String message, long retryAfterSeconds) {
            super(ErrorCode.RATE_LIMITED, message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    // ── Other exceptions ─────────────────────────────────────────────────

    /** Raised when circuit breaker trips. */
    public static class CircuitBreakerError extends RuntimeException {
        private final String reason;
        private final long tokensUsed;
        private final double costUsd;

        public CircuitBreakerError(String reason, long tokensUsed, double costUsd) {
            super("Circuit breaker tripped: " + reason
                    + " (tokens=" + tokensUsed + ", cost=$" + String.format("%.4f", costUsd) + ")");
            this.reason = reason;
            this.tokensUsed = tokensUsed;
            this.costUsd = costUsd;
        }

        public String getReason() { return reason; }
        public long getTokensUsed() { return tokensUsed; }
        public double getCostUsd() { return costUsd; }
    }

    /** Raised when an interaction request times out. */
    public static class InteractionTimeoutError extends RuntimeException {
        private final String requestId;

        public InteractionTimeoutError(String requestId, double timeout) {
            super("Interaction request '" + requestId + "' timed out after " + timeout + "s");
            this.requestId = requestId;
        }

        public String getRequestId() { return requestId; }
    }

    private CodyErrors() {}
}
