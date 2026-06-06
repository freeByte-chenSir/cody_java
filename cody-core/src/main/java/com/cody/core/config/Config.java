package com.cody.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Complete configuration system .
 *
 * Load order (later overrides earlier):
 *   1. Built-in defaults (field defaults in sub-configs)
 *   2. Global config: ~/.cody/config.json
 *   3. Project config: &lt;workdir&gt;/.cody/config.json
 *   4. Environment variables
 */
public class Config {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Top-level fields ─────────────────────────────────────────────────

    private String model = "claude-sonnet-4-0";
    private String modelBaseUrl;
    private String modelApiKey;
    private String smallModel;
    private String smallModelBaseUrl;
    private String smallModelApiKey;
    private boolean enableThinking;
    private Integer thinkingBudget;

    private AuthConfig auth = new AuthConfig();
    private SkillConfig skills = new SkillConfig();
    private MCPConfig mcp = new MCPConfig();
    private SecurityConfig security = new SecurityConfig();
    private ToolPermissionConfig permissions = new ToolPermissionConfig();
    private RateLimitConfig rateLimit = new RateLimitConfig();
    private TruncationConfig truncation = new TruncationConfig();
    private CompactionConfig compaction = new CompactionConfig();
    private InteractionConfig interaction = new InteractionConfig();
    private RetryConfig retry = new RetryConfig();
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    // ── Static factory: load with full hierarchy ─────────────────────────

    /**
     * Load Config with full hierarchy: defaults → global → project → env.
          */
    public static Config load(Path workdir) {
        Config config = new Config();

        // Layer 2: global config (~/.cody/config.json)
        Path globalPath = Paths.get(System.getProperty("user.home"), ".cody", "config.json");
        config.deepMerge(loadJson(globalPath));

        // Layer 3: project config (<workdir>/.cody/config.json)
        if (workdir != null) {
            Path projectPath = workdir.resolve(".cody").resolve("config.json");
            config.deepMerge(loadJson(projectPath));
        }

        // Layer 4: environment variable overrides
        config.applyEnvOverrides();

        return config;
    }

    /** Load JSON from file, returning empty map if file doesn't exist. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadJson(Path path) {
        if (!Files.exists(path)) return Map.of();
        try {
            return MAPPER.readValue(path.toFile(), Map.class);
        } catch (IOException e) {
            return Map.of();
        }
    }

    /** Deep-merge a map of overrides into this config. */
    @SuppressWarnings("unchecked")
    public void deepMerge(Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) return;
        if (overrides.containsKey("model")) model = (String) overrides.get("model");
        if (overrides.containsKey("model_base_url")) modelBaseUrl = (String) overrides.get("model_base_url");
        if (overrides.containsKey("model_api_key")) modelApiKey = (String) overrides.get("model_api_key");
        if (overrides.containsKey("small_model")) smallModel = (String) overrides.get("small_model");
        if (overrides.containsKey("small_model_base_url")) smallModelBaseUrl = (String) overrides.get("small_model_base_url");
        if (overrides.containsKey("small_model_api_key")) smallModelApiKey = (String) overrides.get("small_model_api_key");
        if (overrides.containsKey("enable_thinking")) enableThinking = (Boolean) overrides.get("enable_thinking");
        if (overrides.containsKey("thinking_budget")) thinkingBudget = (Integer) overrides.get("thinking_budget");

        if (overrides.containsKey("auth")) auth.deepMerge((Map<String, Object>) overrides.get("auth"));
        if (overrides.containsKey("skills")) skills.deepMerge((Map<String, Object>) overrides.get("skills"));
        if (overrides.containsKey("mcp")) mcp.deepMerge((Map<String, Object>) overrides.get("mcp"));
        if (overrides.containsKey("security")) security.deepMerge((Map<String, Object>) overrides.get("security"));
        if (overrides.containsKey("permissions")) permissions.deepMerge((Map<String, Object>) overrides.get("permissions"));
        if (overrides.containsKey("rate_limit")) rateLimit.deepMerge((Map<String, Object>) overrides.get("rate_limit"));
        if (overrides.containsKey("truncation")) truncation.deepMerge((Map<String, Object>) overrides.get("truncation"));
        if (overrides.containsKey("compaction")) compaction.deepMerge((Map<String, Object>) overrides.get("compaction"));
        if (overrides.containsKey("interaction")) interaction.deepMerge((Map<String, Object>) overrides.get("interaction"));
        if (overrides.containsKey("retry")) retry.deepMerge((Map<String, Object>) overrides.get("retry"));
        if (overrides.containsKey("circuit_breaker")) circuitBreaker.deepMerge((Map<String, Object>) overrides.get("circuit_breaker"));
    }

    /** Apply environment variable overrides (highest priority). */
    private void applyEnvOverrides() {
        setIfEnv("CODY_MODEL", v -> model = v);
        setIfEnv("CODY_MODEL_BASE_URL", v -> modelBaseUrl = v);
        setIfEnv("CODY_MODEL_API_KEY", v -> modelApiKey = v);
        setIfEnv("CODY_CODING_PLAN_KEY", v -> modelApiKey = v); // legacy fallback
        setIfEnv("CODY_ENABLE_THINKING", v -> enableThinking = Boolean.parseBoolean(v));
        setIfEnv("CODY_THINKING_BUDGET", v -> thinkingBudget = Integer.parseInt(v));
        setIfEnv("CODY_SMALL_MODEL", v -> smallModel = v);
        setIfEnv("CODY_SMALL_MODEL_BASE_URL", v -> smallModelBaseUrl = v);
        setIfEnv("CODY_SMALL_MODEL_API_KEY", v -> smallModelApiKey = v);
        setIfEnv("CODY_COMPACTION_USE_LLM", v -> compaction.useLlm = Boolean.parseBoolean(v));
        setIfEnv("CODY_COMPACTION_MODEL", v -> compaction.model = v);
        setIfEnv("CODY_SKILL_DIRS", v -> skills.customDirs = List.of(v.split(",")));
    }

    private void setIfEnv(String name, java.util.function.Consumer<String> setter) {
        String val = System.getenv(name);
        if (val != null && !val.isEmpty()) setter.accept(val);
    }

    /** Runtime overrides (CLI args / SDK builder). Non-null values are applied. */
    public void applyOverrides(String model, String baseUrl, String apiKey,
                                Boolean thinking, Integer thinkingBudget,
                                List<String> skillDirs, List<String> extraRoots) {
        if (model != null) this.model = model;
        if (baseUrl != null) this.modelBaseUrl = baseUrl;
        if (apiKey != null) this.modelApiKey = apiKey;
        if (thinking != null) this.enableThinking = thinking;
        if (thinkingBudget != null) this.thinkingBudget = thinkingBudget;
        if (skillDirs != null && !skillDirs.isEmpty()) {
            List<String> merged = new ArrayList<>(skills.customDirs);
            for (String d : skillDirs) if (!merged.contains(d)) merged.add(d);
            skills.customDirs = merged;
        }
        if (extraRoots != null && !extraRoots.isEmpty()) {
            List<String> merged = new ArrayList<>(security.allowedRoots);
            for (String r : extraRoots) if (!merged.contains(r)) merged.add(r);
            security.allowedRoots = merged;
        }
    }

    // ── Sub-configs ──────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuthConfig {
        private String type = "api_key";
        private String token;
        private String refreshToken;
        private String apiKey;
        // expiresAt is a timestamp, not serialized in same way

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getToken() { return token; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        @SuppressWarnings("unchecked")
        void deepMerge(Map<String, Object> m) {
            if (m.containsKey("type")) type = (String) m.get("type");
            if (m.containsKey("api_key")) apiKey = (String) m.get("api_key");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkillConfig {
        private List<String> enabled = new ArrayList<>();
        private List<String> disabled = new ArrayList<>();
        private List<String> customDirs = new ArrayList<>();

        public List<String> getEnabled() { return enabled; }
        public List<String> getDisabled() { return disabled; }
        public List<String> getCustomDirs() { return customDirs; }
        public void setCustomDirs(List<String> customDirs) { this.customDirs = customDirs; }

        @SuppressWarnings("unchecked")
        void deepMerge(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = (List<String>) m.get("enabled");
            if (m.containsKey("disabled")) disabled = (List<String>) m.get("disabled");
            if (m.containsKey("custom_dirs")) customDirs = (List<String>) m.get("custom_dirs");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MCPConfig {
        private List<MCPServerConfig> servers = new ArrayList<>();

        public List<MCPServerConfig> getServers() { return servers; }
        public void setServers(List<MCPServerConfig> servers) { this.servers = servers; }

        @SuppressWarnings("unchecked")
        void deepMerge(Map<String, Object> m) {
            if (m.containsKey("servers")) {
                List<Map<String, Object>> raw = (List<Map<String, Object>>) m.get("servers");
                servers = raw.stream().map(MCPServerConfig::fromMap).collect(Collectors.toList());
            }
        }
    }

    public static class MCPServerConfig {
        private String name;
        private String transport = "stdio";
        private String command;
        private List<String> args = new ArrayList<>();
        private Map<String, String> env = new HashMap<>();
        private String url;
        private Map<String, String> headers = new HashMap<>();

        public String getName() { return name; }
        public String getTransport() { return transport; }
        public String getCommand() { return command; }
        public List<String> getArgs() { return args; }
        public Map<String, String> getEnv() { return env; }
        public String getUrl() { return url; }
        public Map<String, String> getHeaders() { return headers; }

        @SuppressWarnings("unchecked")
        static MCPServerConfig fromMap(Map<String, Object> m) {
            MCPServerConfig c = new MCPServerConfig();
            c.name = (String) m.get("name");
            c.transport = (String) m.getOrDefault("transport", "stdio");
            c.command = (String) m.get("command");
            c.args = (List<String>) m.getOrDefault("args", List.of());
            c.env = (Map<String, String>) m.getOrDefault("env", Map.of());
            c.url = (String) m.get("url");
            c.headers = (Map<String, String>) m.getOrDefault("headers", Map.of());
            return c;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SecurityConfig {
        private List<String> allowedCommands = new ArrayList<>();
        private List<String> blockedCommands = new ArrayList<>();
        private List<String> restrictedPaths = new ArrayList<>();
        private List<String> allowedRoots = new ArrayList<>();
        private boolean strictReadBoundary;
        private boolean requireConfirmation = true;
        private boolean allowPrivateUrls;
        private int commandTimeout = 30;

        public List<String> getAllowedCommands() { return allowedCommands; }
        public List<String> getBlockedCommands() { return blockedCommands; }
        public List<String> getAllowedRoots() { return allowedRoots; }
        public boolean isStrictReadBoundary() { return strictReadBoundary; }
        public boolean isRequireConfirmation() { return requireConfirmation; }
        public int getCommandTimeout() { return commandTimeout; }

        @SuppressWarnings("unchecked")
        void deepMerge(Map<String, Object> m) {
            if (m.containsKey("allowed_commands")) allowedCommands = (List<String>) m.get("allowed_commands");
            if (m.containsKey("blocked_commands")) blockedCommands = (List<String>) m.get("blocked_commands");
            if (m.containsKey("allowed_roots")) allowedRoots = (List<String>) m.get("allowed_roots");
            if (m.containsKey("strict_read_boundary")) strictReadBoundary = (Boolean) m.get("strict_read_boundary");
            if (m.containsKey("command_timeout")) commandTimeout = ((Number) m.get("command_timeout")).intValue();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolPermissionConfig {
        private Map<String, String> overrides = new HashMap<>();
        private String defaultLevel = "confirm";

        public Map<String, String> getOverrides() { return overrides; }
        public String getDefaultLevel() { return defaultLevel; }

        @SuppressWarnings("unchecked")
        void deepMerge(Map<String, Object> m) {
            if (m.containsKey("overrides")) overrides = (Map<String, String>) m.get("overrides");
            if (m.containsKey("default_level")) defaultLevel = (String) m.get("default_level");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RateLimitConfig {
        private boolean enabled;
        private int maxRequests = 60;
        private double windowSeconds = 60.0;

        public boolean isEnabled() { return enabled; }
        public int getMaxRequests() { return maxRequests; }
        public double getWindowSeconds() { return windowSeconds; }

        @SuppressWarnings("unchecked")
        void deepMerge(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = (Boolean) m.get("enabled");
            if (m.containsKey("max_requests")) maxRequests = ((Number) m.get("max_requests")).intValue();
            if (m.containsKey("window_seconds")) windowSeconds = ((Number) m.get("window_seconds")).doubleValue();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TruncationConfig {
        private boolean enabled = true;
        private int maxOutputChars = 120_000;

        public boolean isEnabled() { return enabled; }
        public int getMaxOutputChars() { return maxOutputChars; }

        @SuppressWarnings("unchecked")
        void deepMerge(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = (Boolean) m.get("enabled");
            if (m.containsKey("max_output_chars")) maxOutputChars = ((Number) m.get("max_output_chars")).intValue();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CompactionConfig {
        private boolean useLlm;
        private String model;
        private int maxTokens = 100_000;
        private double triggerRatio;
        private int contextWindowTokens;
        private int keepRecent = 4;
        private int maxSummaryTokens = 500;
        private boolean enablePruning = true;
        private int pruneProtectTokens = 40_000;
        private int pruneMinSavingTokens = 20_000;
        private int pruneMinContentTokens = 200;

        public boolean isUseLlm() { return useLlm; }
        public String getModel() { return model; }
        public int getMaxTokens() { return maxTokens; }
        public double getTriggerRatio() { return triggerRatio; }
        public int getContextWindowTokens() { return contextWindowTokens; }
        public int getKeepRecent() { return keepRecent; }
        public int getMaxSummaryTokens() { return maxSummaryTokens; }
        public boolean isEnablePruning() { return enablePruning; }
        public int getPruneProtectTokens() { return pruneProtectTokens; }
        public int getPruneMinSavingTokens() { return pruneMinSavingTokens; }
        public int getPruneMinContentTokens() { return pruneMinContentTokens; }

        /** Effective max tokens: triggerRatio * contextWindowTokens or maxTokens. */
        public int effectiveMaxTokens() {
            if (triggerRatio > 0 && contextWindowTokens > 0) {
                return (int) (triggerRatio * contextWindowTokens);
            }
            return maxTokens;
        }

        @SuppressWarnings("unchecked")
        void deepMerge(Map<String, Object> m) {
            if (m.containsKey("use_llm")) useLlm = (Boolean) m.get("use_llm");
            if (m.containsKey("model")) model = (String) m.get("model");
            if (m.containsKey("max_tokens")) maxTokens = ((Number) m.get("max_tokens")).intValue();
            if (m.containsKey("trigger_ratio")) triggerRatio = ((Number) m.get("trigger_ratio")).doubleValue();
            if (m.containsKey("context_window_tokens")) contextWindowTokens = ((Number) m.get("context_window_tokens")).intValue();
            if (m.containsKey("keep_recent")) keepRecent = ((Number) m.get("keep_recent")).intValue();
            if (m.containsKey("max_summary_tokens")) maxSummaryTokens = ((Number) m.get("max_summary_tokens")).intValue();
            if (m.containsKey("enable_pruning")) enablePruning = (Boolean) m.get("enable_pruning");
            if (m.containsKey("prune_protect_tokens")) pruneProtectTokens = ((Number) m.get("prune_protect_tokens")).intValue();
            if (m.containsKey("prune_min_saving_tokens")) pruneMinSavingTokens = ((Number) m.get("prune_min_saving_tokens")).intValue();
            if (m.containsKey("prune_min_content_tokens")) pruneMinContentTokens = ((Number) m.get("prune_min_content_tokens")).intValue();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InteractionConfig {
        private boolean enabled;
        private double timeout = 30.0;

        public boolean isEnabled() { return enabled; }
        public double getTimeout() { return timeout; }

        @SuppressWarnings("unchecked")
        void deepMerge(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = (Boolean) m.get("enabled");
            if (m.containsKey("timeout")) timeout = ((Number) m.get("timeout")).doubleValue();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RetryConfig {
        private boolean enabled = true;
        private int maxRetries = 3;
        private double baseDelay = 2.0;
        private double maxDelay = 30.0;

        public boolean isEnabled() { return enabled; }
        public int getMaxRetries() { return maxRetries; }
        public double getBaseDelay() { return baseDelay; }
        public double getMaxDelay() { return maxDelay; }

        @SuppressWarnings("unchecked")
        void deepMerge(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = (Boolean) m.get("enabled");
            if (m.containsKey("max_retries")) maxRetries = ((Number) m.get("max_retries")).intValue();
            if (m.containsKey("base_delay")) baseDelay = ((Number) m.get("base_delay")).doubleValue();
            if (m.containsKey("max_delay")) maxDelay = ((Number) m.get("max_delay")).doubleValue();
        }
    }

    /** Circuit breaker configuration: token limit, cost limit, step limit, and loop detection. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CircuitBreakerConfig {
        private boolean enabled = true;
        private long maxTokens = 1_000_000;
        private double maxCostUsd = 10.0;
        private int maxSteps; // 0 = unlimited
        private int loopDetectTurns = 6;
        private double loopSimilarityThreshold = 0.9;
        private Map<String, Double> modelPrices = new HashMap<>();
        {
            modelPrices.put("default", 0.000003);
        }

        public boolean isEnabled() { return enabled; }
        public long getMaxTokens() { return maxTokens; }
        public double getMaxCostUsd() { return maxCostUsd; }
        public int getMaxSteps() { return maxSteps; }
        public int getLoopDetectTurns() { return loopDetectTurns; }
        public double getLoopSimilarityThreshold() { return loopSimilarityThreshold; }
        public Map<String, Double> getModelPrices() { return modelPrices; }

        /** Get price per token for the given model, falling back to default. */
        public double getPricePerToken(String model) {
            return modelPrices.getOrDefault(model, modelPrices.getOrDefault("default", 0.000003));
        }

        @SuppressWarnings("unchecked")
        void deepMerge(Map<String, Object> m) {
            if (m.containsKey("enabled")) enabled = (Boolean) m.get("enabled");
            if (m.containsKey("max_tokens")) maxTokens = ((Number) m.get("max_tokens")).longValue();
            if (m.containsKey("max_cost_usd")) maxCostUsd = ((Number) m.get("max_cost_usd")).doubleValue();
            if (m.containsKey("max_steps")) maxSteps = ((Number) m.get("max_steps")).intValue();
            if (m.containsKey("loop_detect_turns")) loopDetectTurns = ((Number) m.get("loop_detect_turns")).intValue();
            if (m.containsKey("loop_similarity_threshold")) loopSimilarityThreshold = ((Number) m.get("loop_similarity_threshold")).doubleValue();
            if (m.containsKey("model_prices")) modelPrices = (Map<String, Double>) m.get("model_prices");
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public String getModel() { return model; }
    public String getModelBaseUrl() { return modelBaseUrl; }
    public String getModelApiKey() { return modelApiKey; }
    public String getSmallModel() { return smallModel; }
    public String getSmallModelBaseUrl() { return smallModelBaseUrl; }
    public String getSmallModelApiKey() { return smallModelApiKey; }
    public boolean isEnableThinking() { return enableThinking; }
    public Integer getThinkingBudget() { return thinkingBudget; }
    public AuthConfig getAuth() { return auth; }
    public SkillConfig getSkills() { return skills; }
    public MCPConfig getMcp() { return mcp; }
    public SecurityConfig getSecurity() { return security; }
    public ToolPermissionConfig getPermissions() { return permissions; }
    public RateLimitConfig getRateLimit() { return rateLimit; }
    public TruncationConfig getTruncation() { return truncation; }
    public CompactionConfig getCompaction() { return compaction; }
    public InteractionConfig getInteraction() { return interaction; }
    public RetryConfig getRetry() { return retry; }
    public CircuitBreakerConfig getCircuitBreaker() { return circuitBreaker; }

    // ── Setters (for Builder pattern) ────────────────────────────────────

    public void setModel(String model) { this.model = model; }
    public void setModelBaseUrl(String modelBaseUrl) { this.modelBaseUrl = modelBaseUrl; }
    public void setModelApiKey(String modelApiKey) { this.modelApiKey = modelApiKey; }
    public void setSmallModel(String smallModel) { this.smallModel = smallModel; }
    public void setSmallModelBaseUrl(String smallModelBaseUrl) { this.smallModelBaseUrl = smallModelBaseUrl; }
    public void setSmallModelApiKey(String smallModelApiKey) { this.smallModelApiKey = smallModelApiKey; }
    public void setEnableThinking(boolean enableThinking) { this.enableThinking = enableThinking; }
    public void setThinkingBudget(Integer thinkingBudget) { this.thinkingBudget = thinkingBudget; }
    public void setAuth(AuthConfig auth) { this.auth = auth; }
    public void setSkills(SkillConfig skills) { this.skills = skills; }
    public void setMcp(MCPConfig mcp) { this.mcp = mcp; }
    public void setSecurity(SecurityConfig security) { this.security = security; }
    public void setPermissions(ToolPermissionConfig permissions) { this.permissions = permissions; }
    public void setRateLimit(RateLimitConfig rateLimit) { this.rateLimit = rateLimit; }
    public void setTruncation(TruncationConfig truncation) { this.truncation = truncation; }
    public void setCompaction(CompactionConfig compaction) { this.compaction = compaction; }
    public void setInteraction(InteractionConfig interaction) { this.interaction = interaction; }
    public void setRetry(RetryConfig retry) { this.retry = retry; }
    public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) { this.circuitBreaker = circuitBreaker; }
}
