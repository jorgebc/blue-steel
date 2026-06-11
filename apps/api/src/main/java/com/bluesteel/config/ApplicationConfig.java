package com.bluesteel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Cross-cutting infrastructure beans: async executor and shared UTC clock. */
@Configuration
@EnableAsync
@EnableScheduling
public class ApplicationConfig {

  // @Primary keeps unqualified @Async resolving here now that a second TaskExecutor bean
  // (queryTaskExecutor) exists — without it Spring falls back to an unbounded executor.
  @Bean
  @Primary
  public TaskExecutor applicationTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // Sized for the 512 MB Render free tier: a small pool aligned with the DB pool, plus a
    // bounded queue so background embedding work (D-063) cannot grow heap without limit.
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(3);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("blue-steel-async-");
    // Let in-flight embedding work drain on deploy/restart instead of being dropped.
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }

  @Bean
  public TaskExecutor queryTaskExecutor(
      @Value("${query.executor.pool-size:2}") int poolSize,
      @Value("${query.executor.queue-capacity:20}") int queueCapacity) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // Dedicated pool for the synchronous Query Mode LLM call (D-052): keeps it off the JVM-wide
    // commonPool (starvation risk on the few-core Render free tier) and isolated from the
    // embedding executor. Pool + queue also bound the worst-case cost-cap overshoot (D-096);
    // saturation rejects rather than growing heap — unreachable below the per-user rate limit.
    executor.setCorePoolSize(poolSize);
    executor.setMaxPoolSize(poolSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("blue-steel-query-");
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

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
