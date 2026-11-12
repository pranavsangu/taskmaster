package com.trustdesk.eval;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class EvalConfig {

    /**
     * Eval runs execute here, one at a time, so a long run never blocks HTTP requests and two runs
     * never compete for the AI provider's rate limit. Up to 5 more runs can wait in the queue.
     */
    @Bean
    public ThreadPoolTaskExecutor evalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(5);
        executor.setThreadNamePrefix("eval-");
        executor.initialize();
        return executor;
    }
}
