package com.finovago.p2p.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Map;
import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(5); // = minimum number of threads to keep in the pool, even if they are idle
        executor.setMaxPoolSize(10); // = maximum number of threads to allow in the pool
        executor.setQueueCapacity(100); // = the capacity of the queue to hold tasks before they are executed

        executor.setTaskDecorator(new MdcTaskDecorator()); // = before executing a task, go into the decorator

        executor.initialize(); // start the executor, initialize the thread pool
        return executor; // save this as a pool used by the @Async annotation
    }

    static class MdcTaskDecorator implements TaskDecorator { // intercepts every async task

        @Override
        public Runnable decorate(Runnable runnable) { // spring gives the runnable (a task) before executing it

            Map<String, String> contextMap = MDC.getCopyOfContextMap(); // = copy the current thread's MDC context map (correlationId) 

            return () -> { // we return a new task
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap); // inject the MDC context map into the new thread's MDC context map (correlationId)
                    }
                    runnable.run(); // exectue the given task in the new thread
                } finally {
                    MDC.clear(); // clear MDC when finished
                }
            };
        }
    }
}