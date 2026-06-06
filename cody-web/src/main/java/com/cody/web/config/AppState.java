package com.cody.web.config;

import com.cody.core.config.Config;
import com.cody.core.deps.CodyDeps;
import com.cody.core.runner.AgentRunner;
import com.cody.core.session.SessionStore;
import com.cody.core.skill_impl.SkillManager;
import com.cody.sdk.Cody;
import com.cody.sdk.CodyClient;
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
        Path dbPath = Path.of(System.getProperty("user.home"), ".cody", "sessions.db");
        return new SessionStore(dbPath);
    }

    @Bean
    public CodyClient codyClient(Config config) {
        return Cody.builder()
                .workdir(Path.of("."))
                .autoApprove(true)
                .build();
    }

    @Bean
    public SkillManager skillManager(Config config) {
        return new SkillManager(config, Path.of("."));
    }
}
