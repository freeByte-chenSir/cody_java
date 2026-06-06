package com.jody.sdk.error;

/**
 * SDK error types*/
public class SdkErrors {

    /** SDK connection / configuration error. */
    public static class SdkConnectionError extends RuntimeException {
        public SdkConnectionError(String message) { super(message); }
        public SdkConnectionError(String message, Throwable cause) { super(message, cause); }
    }

    /** SDK timeout error. */
    public static class SdkTimeoutError extends RuntimeException {
        public SdkTimeoutError(String message) { super(message); }
    }

    /** SDK configuration validation error. */
    public static class SdkConfigError extends RuntimeException {
        public SdkConfigError(String message) { super(message); }
    }

    /** Agent run cancelled. */
    public static class RunCancelledError extends RuntimeException {
        public RunCancelledError() { super("Run was cancelled"); }
    }
}
