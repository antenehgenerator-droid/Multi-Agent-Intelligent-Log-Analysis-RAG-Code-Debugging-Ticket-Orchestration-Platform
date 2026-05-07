package com.platform.api.config;

import com.platform.core.util.LogNormalizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlatformConfig {

  @Bean
  public LogNormalizer logNormalizer() {
    return new LogNormalizer();
  }
}
