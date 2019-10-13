package org.apache.skywalking.apm.testcase.spring.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @author zhaoyuguang
 */
@Configuration
public class AsyncConfig {

    @Bean(value = "customizeAsync")
    public AsyncTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setMaxPoolSize(10);
        taskExecutor.setCorePoolSize(2);
        taskExecutor.setThreadNamePrefix("customize-async-thread-pool");
        taskExecutor.initialize();
        return taskExecutor;
    }

    @Bean
    public AsyncBean asyncBean() {
        return new AsyncBean();
    }

    @Bean
    public HttpBean httpBean() {
        return new HttpBean();
    }
}
