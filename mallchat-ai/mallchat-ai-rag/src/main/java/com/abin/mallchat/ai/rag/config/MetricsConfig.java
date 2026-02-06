package com.abin.mallchat.ai.rag.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 指标配置类
 * 配置 Micrometer 和 Prometheus 集成
 * 
 * @author AI Assistant
 * @date 2025-01-07
 */
@Configuration
public class MetricsConfig {

    /**
     * 启用 @Timed 注解支持
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
