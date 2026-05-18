package com.platform.llm.config;

import com.platform.llm.util.DeterministicFakeModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

public class LLmConfig {

    @Value("${llm.ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${llm.ollama.model-name:gemma4:e2b}")
    private String modelName;

    @Bean(name = "cheapModel")
    @ConditionalOnProperty(name = "llm.provider" ,havingValue = "ollama")
    public ChatLanguageModel cheapModel(){
        return OllamaChatModel.builder()
            .baseUrl(baseUrl)
            .modelName(modelName)
            .timeout(Duration.ofSeconds(40))
            .temperature(0.2)
            .build();
    }

    @Bean(name = "midModel")
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
    public ChatLanguageModel midModel() {
        return cheapModel(); // Shared local footprint for local Gemma deployment topologies
    }

    @Bean(name = "largeModel")
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
    public ChatLanguageModel largeModel() {
        return cheapModel();
    }

    // Fallback Mocking layer for automated isolation compilation paths (CI Pipelines)
    @Bean(name = "cheapModel")
    @ConditionalOnProperty(name = "llm.provider", havingValue = "mock", matchIfMissing = true)
    public ChatLanguageModel fallbackFakeModel() {
        return new DeterministicFakeModel();
    }
}
