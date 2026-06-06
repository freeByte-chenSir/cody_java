package com.jody.sdk;

import com.jody.core.config.Config;
import com.jody.core.runner.AgentRunner;
import com.jody.sdk.config.SdkConfig;

import java.nio.file.Path;

/**
 * SDK entry point with builder pattern.
 *
 *
 * Usage:
 * <pre>{@code
 * JodyClient client = Jody.builder()
 *     .model("claude-sonnet-4-0")
 *     .apiKey("sk-ant-...")
 *     .workdir(Path.of("/project"))
 *     .build();
 * AgentRunner.RunResult result = client.run("Create a hello world program");
 * }</pre>
 */
public class Jody {

    /** Create a new builder for constructing a JodyClient. */
    public static JodyBuilder builder() {
        return new JodyBuilder();
    }

    /** Quick one-shot run with default config. */
    public static AgentRunner.RunResult run(String prompt, Path workdir) {
        return builder().workdir(workdir).build().run(prompt);
    }

    public static class JodyBuilder {
        private final SdkConfig sdkConfig = new SdkConfig();
        private Path workdir = Path.of(".");

        public JodyBuilder model(String m) { sdkConfig.setModel(m); return this; }
        public JodyBuilder apiKey(String k) { sdkConfig.setApiKey(k); return this; }
        public JodyBuilder baseUrl(String u) { sdkConfig.setBaseUrl(u); return this; }
        public JodyBuilder enableThinking(boolean t) { sdkConfig.setEnableThinking(t); return this; }
        public JodyBuilder thinkingBudget(int b) { sdkConfig.setThinkingBudget(b); return this; }
        public JodyBuilder workdir(Path w) { this.workdir = w; return this; }
        public JodyBuilder autoApprove(boolean a) { sdkConfig.setAutoApprove(a); return this; }

        /** Build the JodyClient. */
        public JodyClient build() {
            Config config = Config.load(workdir);
            sdkConfig.applyTo(config);
            return new JodyClient(config, workdir, sdkConfig);
        }
    }
}
