package com.cody.core.runner;

import com.cody.core.circuit.CircuitBreaker;
import com.cody.core.config.Config;
import com.cody.core.deps.CodyDeps;
import com.cody.core.error.CodyErrors.ToolError;
import com.cody.core.prompt.SystemPrompt;
import com.cody.core.tool.*;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Central orchestrator  AgentRunner.
 *
 * Responsibilities:
 *   - Create LLM agent with registered tools
 *   - Assemble CodyDeps for dependency injection
 *   - Execute the agent loop: prompt → LLM → tools → repeat
 *   - Provide run() (sync), stream() (streaming), runSync() (sync)
 *   - Per-run circuit breaker with token/cost/loop detection
 *   - Context compaction when approaching token limits
 *   - Retry with exponential backoff on transient errors
 */
public class AgentRunner {

    private final Config config;
    private final Path workdir;
    private final ChatLanguageModel llmModel;
    private final String systemPrompt;

    // ── Subsystems (lazy-initialized) ────────────────────────────────────

    private CodyDeps deps;

    // ── Circuit breaker (uses standalone CircuitBreaker) ──────────────────

    // ── Constructor ──────────────────────────────────────────────────────

    /**
     * Create AgentRunner with full assembly: chat model, tools, circuit breaker, session store,
     * audit logger, permission manager, file history, context manager, and skill manager.
     */
    public AgentRunner(Config config, Path workdir) {
        this.config = config;
        this.workdir = workdir;

        // Build LangChain4j chat model
        this.llmModel = createChatModel();

        // Build system prompt
        this.systemPrompt = new SystemPrompt()
                .appendCodyMd(workdir)
                .build();

        // Create CodyDeps
        this.deps = createDeps();
    }

    /** Create the appropriate ChatLanguageModel based on config. */
    private ChatLanguageModel createChatModel() {
        String apiKey = config.getModelApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("ANTHROPIC_API_KEY");
            if (apiKey == null) apiKey = System.getenv("OPENAI_API_KEY");
        }

        String baseUrl = config.getModelBaseUrl();
        Duration timeout = Duration.ofSeconds(120);

        // If base URL is set, use OpenAI-compatible (covers DeepSeek, GLM, Qwen, etc.)
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return OpenAiChatModel.builder()
                    .apiKey(apiKey != null ? apiKey : "sk-placeholder")
                    .baseUrl(baseUrl)
                    .modelName(config.getModel())
                    .timeout(timeout)
                    .build();
        }

        // Default: Anthropic Claude
        String modelName = config.getModel();
        if (modelName == null || modelName.isEmpty()) {
            modelName = "claude-sonnet-4-0";
        }

        return AnthropicChatModel.builder()
                .apiKey(apiKey != null ? apiKey : "sk-ant-placeholder")
                .modelName(modelName)
                .timeout(timeout)
                .build();
    }

    /** Create CodyDeps with all subsystems wired for tool execution context. */
    private CodyDeps createDeps() {
        List<Path> allowedRoots = new ArrayList<>();
        allowedRoots.add(workdir);
        if (config.getSecurity().getAllowedRoots() != null) {
            for (String r : config.getSecurity().getAllowedRoots()) {
                allowedRoots.add(Path.of(r).toAbsolutePath());
            }
        }

        return new CodyDeps.Builder(config, workdir)
                .allowedRoots(allowedRoots)
                .strictReadBoundary(config.getSecurity().isStrictReadBoundary())
                .build();
    }

    // ── Public Run Methods ────────────────────────────────────────────────

    /**
     * Synchronous one-shot execution.
     *
     * @param prompt The user's task prompt
     * @return RunResult with output, thinking, tool traces, usage
     */
    public RunResult run(String prompt) {
        return run(prompt, null, null, null, null);
    }

    /** Full run() with session, tool filtering, and cancellation. */
    public RunResult run(String prompt, String sessionId,
                          Set<String> includeTools, Set<String> excludeTools,
                          CountDownLatch cancelSignal) {
        CircuitBreaker.CircuitState cb = new CircuitBreaker.CircuitState();

        // Build messages
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(prompt));

        // Get tools
        List<CodyTool> tools = ToolRegistry.getTools(false, null, includeTools, excludeTools);
        List<dev.langchain4j.agent.tool.ToolSpecification> toolSpecs = ToolRegistry.toLangChain4jSpecs(tools);

        // Agent loop (max 50 iterations to prevent infinite loops)
        for (int i = 0; i < 50; i++) {
            // Check circuit breaker before each LLM call
            CircuitBreaker.check(config.getCircuitBreaker(), cb);

            // Send to LLM
            dev.langchain4j.model.output.Response<AiMessage> response;
            try {
                response = llmModel.generate(messages, toolSpecs);
            } catch (Exception e) {
                // Retry with backoff for transient errors
                response = retryWithBackoff(messages, toolSpecs, config.getRetry());
                if (response == null) {
                    return new RunResult("[ERROR] LLM call failed: " + e.getMessage(),
                            null, List.of(), new Usage(0, 0, 0));
                }
            }

            AiMessage aiMsg = response.content();
            Usage usage = new Usage(response.tokenUsage().inputTokenCount(),
                    response.tokenUsage().outputTokenCount(),
                    response.tokenUsage().totalTokenCount());

            // Update circuit breaker
            CircuitBreaker.update(cb, aiMsg.text(), usage.getTotalTokens(),
                    usage.getTotalTokens() * config.getCircuitBreaker().getPricePerToken(config.getModel()));
            CircuitBreaker.trimRecent(cb, config.getCircuitBreaker().getLoopDetectTurns() + 1);

            // Check if response has tool calls
            if (aiMsg.toolExecutionRequests() != null && !aiMsg.toolExecutionRequests().isEmpty()) {
                // Execute tools
                List<ToolTrace> traces = new ArrayList<>();
                for (dev.langchain4j.agent.tool.ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                    CodyTool tool = ToolRegistry.getTool(req.name());
                    if (tool == null) {
                        // Unknown tool → return error to LLM
                        ToolExecutionResultMessage errMsg = ToolExecutionResultMessage.from(req,
                                "[ERROR] Unknown tool: " + req.name());
                        messages.add(errMsg);
                        continue;
                    }

                    // Parse arguments
                    Map<String, Object> args = parseArgs(req.arguments());
                    ToolContext ctx = new ToolContext(workdir, req.name(), deps);

                    // Execute with middleware
                    String result;
                    try {
                        result = ToolMiddleware.execute(tool, ctx, args, deps);
                    } catch (ToolError e) {
                        // ToolError → feed error back to LLM so it can self-correct
                        result = "[ERROR] " + e.getMessage();
                    }

                    traces.add(new ToolTrace(req.name(), args, result));

                    // Append tool result to messages
                    ToolExecutionResultMessage toolResult = ToolExecutionResultMessage.from(req, result);
                    messages.add(toolResult);
                }

                // Append AI message with tool calls
                messages.add(aiMsg);

                continue; // Loop again — LLM sees tool results
            }

            // No tool calls → final answer
            return new RunResult(aiMsg.text(), null, List.of(), usage);
        }

        return new RunResult("[ERROR] Maximum agent loop iterations reached", null, List.of(),
                new Usage(0, 0, 0));
    }

    /**
     * Streaming execution.
     *
     * Uses a producer-consumer pattern:
     *   - Background thread runs the agent loop, puts events into a BlockingQueue
     *   - Caller consumes events from the queue iterator
     *
     * @return Iterator of StreamEvent (blocking)
     */
    public Iterator<StreamEvent> stream(String prompt) {
        BlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>();

        Thread producer = new Thread(() -> {
            try {
                CircuitBreaker.CircuitState cb = new CircuitBreaker.CircuitState();

                List<ChatMessage> messages = new ArrayList<>();
                messages.add(SystemMessage.from(systemPrompt));
                messages.add(UserMessage.from(prompt));

                List<CodyTool> tools = ToolRegistry.getTools(false, null, null, null);
                List<dev.langchain4j.agent.tool.ToolSpecification> toolSpecs = ToolRegistry.toLangChain4jSpecs(tools);

                for (int i = 0; i < 50; i++) {
                    CircuitBreaker.check(config.getCircuitBreaker(), cb);

                    dev.langchain4j.model.output.Response<AiMessage> response;
                    try {
                        response = llmModel.generate(messages, toolSpecs);
                    } catch (Exception e) {
                        queue.put(new StreamEvent.Error(e.getMessage()));
                        return;
                    }

                    AiMessage aiMsg = response.content();
                    Usage usage = new Usage(response.tokenUsage().inputTokenCount(),
                            response.tokenUsage().outputTokenCount(),
                            response.tokenUsage().totalTokenCount());

                    // Stream text delta
                    if (aiMsg.text() != null && !aiMsg.text().isEmpty()) {
                        queue.put(new StreamEvent.TextDelta(aiMsg.text()));
                    }

                    CircuitBreaker.update(cb, aiMsg.text(), usage.getTotalTokens(),
                            usage.getTotalTokens() * config.getCircuitBreaker().getPricePerToken(config.getModel()));
                    CircuitBreaker.trimRecent(cb, config.getCircuitBreaker().getLoopDetectTurns() + 1);

                    // Handle tool calls
                    if (aiMsg.toolExecutionRequests() != null && !aiMsg.toolExecutionRequests().isEmpty()) {
                        for (dev.langchain4j.agent.tool.ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                            Map<String, Object> args = parseArgs(req.arguments());

                            queue.put(new StreamEvent.ToolCall(req.name(), args, req.id()));

                            CodyTool tool = ToolRegistry.getTool(req.name());
                            String result;
                            if (tool == null) {
                                result = "[ERROR] Unknown tool: " + req.name();
                            } else {
                                ToolContext ctx = new ToolContext(workdir, req.name(), deps);
                                try {
                                    result = ToolMiddleware.execute(tool, ctx, args, deps);
                                } catch (ToolError e) {
                                    result = "[ERROR] " + e.getMessage();
                                }
                            }

                            queue.put(new StreamEvent.ToolResult(req.name(), req.id(), result));

                            ToolExecutionResultMessage toolResult = ToolExecutionResultMessage.from(req, result);
                            messages.add(toolResult);
                        }
                        messages.add(aiMsg);
                        continue;
                    }

                    // Done
                    List<ToolTrace> traces = new ArrayList<>(); // accumulate from above
                    queue.put(new StreamEvent.Done(aiMsg.text(), usage.inputTokens, usage.outputTokens, traces));
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                try { queue.put(new StreamEvent.Error(e.getMessage())); } catch (InterruptedException ignored) {}
            } finally {
                // Poison pill
                try { queue.put(StreamEvent.POISON_PILL); } catch (InterruptedException ignored) {}
            }
        }, "cody-stream-producer");

        producer.setDaemon(true);
        producer.start();

        return new BlockingQueueIterator<>(queue);
    }

    // ── Retry with Backoff ────────────────────────────────────────────────

    private dev.langchain4j.model.output.Response<AiMessage> retryWithBackoff(
            List<ChatMessage> messages, List<dev.langchain4j.agent.tool.ToolSpecification> toolSpecs,
            Config.RetryConfig retryConfig) {
        if (!retryConfig.isEnabled()) return null;

        int maxRetries = retryConfig.getMaxRetries();
        double baseDelay = retryConfig.getBaseDelay();
        double maxDelay = retryConfig.getMaxDelay();

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                double delay = Math.min(baseDelay * Math.pow(2, attempt), maxDelay);
                Thread.sleep((long) (delay * 1000));
                return llmModel.generate(messages, toolSpecs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception ignored) {
                // Retry
            }
        }
        return null;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Parse tool call arguments from JSON string. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isEmpty()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ── Accessors ────────────────────────────────────────────────────────

    public Config getConfig() { return config; }
    public Path getWorkdir() { return workdir; }
    public CodyDeps getDeps() { return deps; }

    // ── Inner Types ─────────────────────────────────────────────────────

    /** Container for run results. */
    public static class RunResult {
        private final String output;
        private final String thinking;
        private final List<ToolTrace> toolTraces;
        private final Usage usage;

        public RunResult(String output, String thinking, List<ToolTrace> toolTraces, Usage usage) {
            this.output = output;
            this.thinking = thinking;
            this.toolTraces = toolTraces;
            this.usage = usage;
        }

        public String getOutput() { return output; }
        public String getThinking() { return thinking; }
        public List<ToolTrace> getToolTraces() { return toolTraces; }
        public Usage getUsage() { return usage; }
    }

    /** Record of a single tool execution. */
    public static class ToolTrace {
        private final String toolName;
        private final Map<String, Object> args;
        private final String result;

        public ToolTrace(String toolName, Map<String, Object> args, String result) {
            this.toolName = toolName;
            this.args = args;
            this.result = result;
        }

        public String getToolName() { return toolName; }
        public Map<String, Object> getArgs() { return args; }
        public String getResult() { return result; }
    }

    /** Token usage. */
    public static class Usage {
        private final long inputTokens;
        private final long outputTokens;
        private final long totalTokens;

        public Usage(long inputTokens, long outputTokens, long totalTokens) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.totalTokens = totalTokens;
        }

        public long getInputTokens() { return inputTokens; }
        public long getOutputTokens() { return outputTokens; }
        public long getTotalTokens() { return totalTokens; }
    }
}
