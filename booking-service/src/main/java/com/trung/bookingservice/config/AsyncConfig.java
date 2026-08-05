package com.trung.bookingservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);        // Số lượng thread mặc định luôn chạy ngầm
        executor.setMaxPoolSize(10);       // Số lượng thread tối đa khi quá tải
        executor.setQueueCapacity(500);    // Hàng đợi chứa các tác vụ Reassign chờ xử lý
        executor.setThreadNamePrefix("BookingAsync-");
        executor.initialize();
        return executor;
    }
}