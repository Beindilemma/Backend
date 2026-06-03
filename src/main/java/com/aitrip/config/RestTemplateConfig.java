package com.aitrip.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // 大模型等接口偶发慢响应，读超时适当放宽（高德短请求也可接受）
        return builder
                .setConnectTimeout(Duration.ofSeconds(15))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
    }
}
