package com.cody.sdk;

import com.cody.core.config.Config;
import com.cody.core.runner.AgentRunner;
import com.cody.sdk.config.SdkConfig;

import java.nio.file.Path;

/**
 * SDK entry point with builder pattern.
 *
 *
 * Usage:
 * <pre>{@code
 * CodyClient client = Cody.builder()
 *     .model("claude-sonnet-4-0")
 *     .apiKey("sk-ant-...")
 *     .workdir(Path.of("/project"))
 *     .build();
 * AgentRunner.RunResult result = client.run("Create a hello world program");
 * }</pre>
 */
public class Cody {

    /** Create a new builder for constructing a CodyClient. */
    public static CodyBuilder builder() {
        return new CodyBuilder();
    }

    /** Quick one-shot run with default config. */
    public static AgentRunner.RunResult run(String prompt, Path workdir) {
        return builder().workdir(workdir).build().run(prompt);
    }

    public static class CodyBuilder {
        private final SdkConfig sdkConfig = new SdkConfig();
        private Path workdir = Path.of(".");

        public CodyBuilder model(String m) { sdkConfig.setModel(m); return this; }
        public CodyBuilder apiKey(String k) { sdkConfig.setApiKey(k); return this; }
        public CodyBuilder baseUrl(String u) { sdkConfig.setBaseUrl(u); return this; }
        public CodyBuilder enableThinking(boolean t) { sdkConfig.setEnableThinking(t); return this; }
        public CodyBuilder thinkingBudget(int b) { sdkConfig.setThinkingBudget(b); return this; }
        public CodyBuilder workdir(Path w) { this.workdir = w; return this; }
        public CodyBuilder autoApprove(boolean a) { sdkConfig.setAutoApprove(a); return this; }

        /** Build the CodyClient. */
        public CodyClient build() {
            Config config = Config.load(workdir);
            sdkConfig.applyTo(config);
            return new CodyClient(config, workdir, sdkConfig);
        }
    }
}
