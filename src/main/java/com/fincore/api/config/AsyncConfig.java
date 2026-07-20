package com.fincore.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean
    public Executor getAsyncExecutor() {
        // @Async work here is I/O-bound (notifications, webhook calls), same as the
        // reconciliation loop in PaymentServiceImpl - a virtual thread per task needs
        // no pool sizing and costs nothing while idle.
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // @Async void methods can't propagate exceptions to a caller - without this
        // handler a failing listener fails silently.
        return (Throwable ex, Method method, Object... params) ->
                log.error("Uncaught exception in async method {}", method.getName(), ex);
    }
}
