package com.diplomat.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LlmConfig {

    @Value("${diplomat.llm.provider:ollama}")
    private String provider;

    @Value("${diplomat.llm.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${diplomat.llm.ollama.model:llama3.1:8b}")
    private String ollamaModel;

    @Value("${diplomat.llm.ollama.temperature:0.7}")
    private double ollamaTemperature;

    @Value("${diplomat.llm.ollama.timeout-seconds:120}")
    private int ollamaTimeout;

    @Value("${diplomat.llm.openai.api-key:}")
    private String openaiApiKey;

    @Value("${diplomat.llm.openai.model:gpt-4o}")
    private String openaiModel;

    @Value("${diplomat.llm.openai.temperature:0.7}")
    private double openaiTemperature;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return switch (provider.toLowerCase()) {
            case "openai" -> OpenAiChatModel.builder()
                    .apiKey(openaiApiKey)
                    .modelName(openaiModel)
                    .temperature(openaiTemperature)
                    .build();
            default -> OllamaChatModel.builder()
                    .baseUrl(ollamaBaseUrl)
                    .modelName(ollamaModel)
                    .temperature(ollamaTemperature)
                    .timeout(Duration.ofSeconds(ollamaTimeout))
                    .build();
        };
    }
}
