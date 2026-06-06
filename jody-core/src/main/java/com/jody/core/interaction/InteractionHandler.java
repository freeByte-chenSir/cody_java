package com.jody.core.interaction;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Human-in-the-loop interaction mechanism .
 *
 * Two modes:
 *   1. AI asks → human responds (InteractionRequest / InteractionResponse)
 *   2. Human proactively sends input (UserInputQueue)
 */
public class InteractionHandler {

    private final Handler delegate;

    public InteractionHandler() {
        this.delegate = Handler.autoApprove();
    }

    public InteractionHandler(Handler delegate) {
        this.delegate = delegate != null ? delegate : Handler.autoApprove();
    }

    public CompletableFuture<InteractionResponse> handle(InteractionRequest request) {
        return delegate.handle(request);
    }

    /**
     * Request from AI to human.
     */
    public static class InteractionRequest {
        public enum Kind { QUESTION, CONFIRM, FEEDBACK }

        private final String id;
        private final Kind kind;
        private final String prompt;
        private final List<String> options;
        private final String context;
        private final double confidence;

        public InteractionRequest(Kind kind, String prompt, List<String> options, String context, double confidence) {
            this.id = UUID.randomUUID().toString();
            this.kind = kind;
            this.prompt = prompt;
            this.options = options;
            this.context = context;
            this.confidence = confidence;
        }

        public String getId() { return id; }
        public Kind getKind() { return kind; }
        public String getPrompt() { return prompt; }
        public List<String> getOptions() { return options; }
        public String getContext() { return context; }
        public double getConfidence() { return confidence; }
    }

    /**
     * Response from human to AI.
     */
    public static class InteractionResponse {
        public enum Action { APPROVE, REJECT, REVISE, ANSWER }

        private final String requestId;
        private final Action action;
        private final String content;

        public InteractionResponse(String requestId, Action action, String content) {
            this.requestId = requestId;
            this.action = action;
            this.content = content;
        }

        public String getRequestId() { return requestId; }
        public Action getAction() { return action; }
        public String getContent() { return content; }
    }

    /**
     * Handler functional interface that receives interaction requests and
     * returns a future response. Used for human-in-the-loop workflows.
     */
    @FunctionalInterface
    public interface Handler {
        CompletableFuture<InteractionResponse> handle(InteractionRequest request);

        /** Default handler that auto-approves everything (used in sync mode). */
        static Handler autoApprove() {
            return request -> CompletableFuture.completedFuture(
                    new InteractionResponse(request.getId(), InteractionResponse.Action.APPROVE, "auto-approved"));
        }
    }
}
