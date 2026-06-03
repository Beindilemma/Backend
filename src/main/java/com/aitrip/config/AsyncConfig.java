package com.aitrip.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 启用 Spring @Async 异步支持。
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
