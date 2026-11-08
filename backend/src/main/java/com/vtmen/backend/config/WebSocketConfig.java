package com.vtmen.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Clients subscribe to /topic/... destinations
        config.enableSimpleBroker("/topic");
        // Messages sent from clients use /app prefix (not needed for server-push only)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Clients connect to ws://<host>/ws (with SockJS fallback)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}
