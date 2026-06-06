package com.cody.web.ws;

import com.cody.sdk.CodyClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final CodyClient client;

    public WebSocketConfig(CodyClient client) {
        this.client = client;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler(), "/ws").setAllowedOrigins("*");
        registry.addHandler(chatHandler(), "/ws/chat/{projectId}").setAllowedOrigins("*");
    }

    @Bean
    public ChatWebSocketHandler chatHandler() {
        return new ChatWebSocketHandler(client);
    }
}
