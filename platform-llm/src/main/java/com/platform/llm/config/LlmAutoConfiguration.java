package com.platform.llm.config;

import com.platform.llm.budget.ModelRouter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan(basePackages = "com.platform.llm")
@EnableConfigurationProperties(ModelRouter.class)
@Import(LLmConfig.class)
public class LlmAutoConfiguration {}
