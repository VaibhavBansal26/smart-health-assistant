package com.healthcare.app.chat;

import com.healthcare.app.recommendation.MlClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    @Bean
    ChatLlm chatLlm(@Value("${app.chat.provider}") String provider,
                    @Value("${app.chat.model}") String model,
                    @Value("${app.chat.effort}") String effort,
                    @Value("${app.chat.max-tokens}") long maxTokens,
                    @Value("${app.chat.anthropic-api-key:}") String apiKey,
                    @Value("${app.chat.anthropic-auth-token:}") String authToken,
                    MlClient ml) {
        boolean hasCredentials = !apiKey.isBlank() || !authToken.isBlank();
        boolean useClaude = provider.equalsIgnoreCase("claude")
                || (provider.equalsIgnoreCase("auto") && hasCredentials);
        return useClaude ? new ClaudeChatLlm(model, effort, maxTokens, apiKey, authToken) : new MockChatLlm(ml);
    }
}
