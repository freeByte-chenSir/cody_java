package com.jody.web.ws;

import com.jody.sdk.JodyClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final JodyClient client;

    public WebSocketConfig(JodyClient client) {
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
