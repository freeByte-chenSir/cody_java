package com.cody.sdk.config;

import com.cody.core.config.Config;

import java.nio.file.Path;
import java.util.*;

/**
 * SDK-level configuration that wraps and overrides core Config settings.
 * Provides a fluent builder API for setting model, API key, base URL,
 * thinking mode, auto-approve, and other SDK-specific options.
 */
public class SdkConfig {

    private String model;
    private String apiKey;
    private String baseUrl;
    private Boolean enableThinking;
    private Integer thinkingBudget;
    private List<String> extraRoots = new ArrayList<>();
    private List<String> includeTools;
    private List<String> excludeTools;
    private boolean autoApprove;
    private Path workdir;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public Boolean getEnableThinking() { return enableThinking; }
    public void setEnableThinking(Boolean enableThinking) { this.enableThinking = enableThinking; }
    public Integer getThinkingBudget() { return thinkingBudget; }
    public void setThinkingBudget(Integer thinkingBudget) { this.thinkingBudget = thinkingBudget; }
    public List<String> getExtraRoots() { return extraRoots; }
    public void setExtraRoots(List<String> extraRoots) { this.extraRoots = extraRoots; }
    public List<String> getIncludeTools() { return includeTools; }
    public void setIncludeTools(List<String> includeTools) { this.includeTools = includeTools; }
    public List<String> getExcludeTools() { return excludeTools; }
    public void setExcludeTools(List<String> excludeTools) { this.excludeTools = excludeTools; }
    public boolean isAutoApprove() { return autoApprove; }
    public void setAutoApprove(boolean autoApprove) { this.autoApprove = autoApprove; }
    public Path getWorkdir() { return workdir; }
    public void setWorkdir(Path workdir) { this.workdir = workdir; }

    /** Apply SDK config overrides to core Config. */
    public void applyTo(Config coreConfig) {
        coreConfig.applyOverrides(model, baseUrl, apiKey, enableThinking, thinkingBudget, null, extraRoots);
    }

    // ── Builder ──────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final SdkConfig config = new SdkConfig();

        public Builder model(String m) { config.model = m; return this; }
        public Builder apiKey(String k) { config.apiKey = k; return this; }
        public Builder baseUrl(String u) { config.baseUrl = u; return this; }
        public Builder enableThinking(Boolean t) { config.enableThinking = t; return this; }
        public Builder thinkingBudget(Integer b) { config.thinkingBudget = b; return this; }
        public Builder extraRoot(String r) { config.extraRoots.add(r); return this; }
        public Builder extraRoots(List<String> r) { config.extraRoots.addAll(r); return this; }
        public Builder includeTools(List<String> t) { config.includeTools = t; return this; }
        public Builder excludeTools(List<String> t) { config.excludeTools = t; return this; }
        public Builder autoApprove(boolean a) { config.autoApprove = a; return this; }
        public Builder workdir(Path w) { config.workdir = w; return this; }
        public SdkConfig build() { return config; }
    }
}
