package com.abin.mallchat.ai.llm.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 调用选项配置类
 * 
 * @author abin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMOptions {
    
    /**
     * 温度参数，控制输出的随机性
     * 范围：0.0 - 2.0
     * 默认：0.7
     */
    @Builder.Default
    private Double temperature = 0.7;
    
    /**
     * 最大生成 token 数量
     * 默认：2000
     */
    @Builder.Default
    private Integer maxTokens = 2000;
    
    /**
     * Top P 采样参数
     * 范围：0.0 - 1.0
     * 默认：1.0
     */
    @Builder.Default
    private Double topP = 1.0;
    
    /**
     * 频率惩罚参数
     * 范围：-2.0 - 2.0
     * 默认：0.0
     */
    @Builder.Default
    private Double frequencyPenalty = 0.0;
    
    /**
     * 存在惩罚参数
     * 范围：-2.0 - 2.0
     * 默认：0.0
     */
    @Builder.Default
    private Double presencePenalty = 0.0;
    
    /**
     * 模型名称
     * 例如：gpt-3.5-turbo, gpt-4, chatglm-6b
     */
    private String model;
    
    /**
     * 创建默认配置
     */
    public static LLMOptions defaultOptions() {
        return LLMOptions.builder().build();
    }
    
    /**
     * 创建用于总结的配置（较低温度）
     */
    public static LLMOptions summaryOptions() {
        return LLMOptions.builder()
                .temperature(0.3)
                .maxTokens(1000)
                .build();
    }
    
    /**
     * 创建用于创意生成的配置（较高温度）
     */
    public static LLMOptions creativeOptions() {
        return LLMOptions.builder()
                .temperature(0.9)
                .maxTokens(2000)
                .build();
    }
}
