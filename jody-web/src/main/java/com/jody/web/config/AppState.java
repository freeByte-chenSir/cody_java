package com.jody.web.config;

import com.jody.core.config.Config;
import com.jody.core.deps.JodyDeps;
import com.jody.core.runner.AgentRunner;
import com.jody.core.session.SessionStore;
import com.jody.core.skill_impl.SkillManager;
import com.jody.sdk.Jody;
import com.jody.sdk.JodyClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Application state management.
 *
 * Manages singletons: Config, SessionStore, SkillManager, AgentRunner.
 * Each is a Spring Bean with proper scoping.
 */
@Configuration
public class AppState {

    @Bean
    public Config config() {
        return Config.load(Path.of("."));
    }

    @Bean
    public SessionStore sessionStore() {
        Path dbPath = Path.of(System.getProperty("user.home"), ".jody", "sessions.db");
        return new SessionStore(dbPath);
    }

    @Bean
    public JodyClient jodyClient(Config config) {
        return Jody.builder()
                .workdir(Path.of("."))
                .autoApprove(true)
                .build();
    }

    @Bean
    public SkillManager skillManager(Config config) {
        return new SkillManager(config, Path.of("."));
    }
}
