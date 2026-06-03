package com.aitrip.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.llm")
public class AiLlmProperties {

    /** OpenAI 兼容 Chat Completions 完整 URL */
    private String chatUrl = "https://api.deepseek.com/v1/chat/completions";
    private String apiKey = "";
    private String model = "deepseek-chat";
    /** 解析失败时最多调用大模型次数（含首次） */
    private int maxRetries = 3;
    private double temperature = 0.2;
    /**
     * 全部重试仍失败后：true 返回空列表并打错误日志；false 抛出 {@link com.aitrip.exception.BusinessException}
     */
    private boolean returnEmptyOnFailure = true;
}
