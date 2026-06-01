package com.abin.mallchat.ai.finetune.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微调服务配置类
 *
 * 配置示例：
 * finetune:
 *   service-url: http://localhost:8000
 *   provider: llamafactory  # llamafactory | axolotl
 *   timeout: 300
 *   max-retries: 3
 *
 * @author abin
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "finetune")
public class FineTuneConfig {

    /**
     * Python 微调服务地址
     */
    private String serviceUrl = "http://localhost:8000";

    /**
     * 微调框架提供商
     * llamafactory: LLaMA-Factory（推荐）
     * axolotl: Axolotl（备选）
     */
    private String provider = "llamafactory";

    /**
     * HTTP 调用超时时间（秒）
     */
    private Integer timeout = 300;

    /**
     * 最大重试次数
     */
    private Integer maxRetries = 3;
}
