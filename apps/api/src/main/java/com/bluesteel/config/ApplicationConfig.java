package com.bluesteel.config;

import java.time.Clock;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Cross-cutting infrastructure beans: async executor and shared UTC clock. */
@Configuration
@EnableAsync
public class ApplicationConfig {

  @Bean
  public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(10);
    executor.setThreadNamePrefix("blue-steel-async-");
    executor.initialize();
    return executor;
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
