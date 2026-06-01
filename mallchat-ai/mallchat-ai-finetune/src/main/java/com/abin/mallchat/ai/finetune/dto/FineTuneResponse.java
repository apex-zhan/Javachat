package com.abin.mallchat.ai.finetune.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 微调训练响应 DTO
 *
 * @author abin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FineTuneResponse {

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 任务状态
     * pending | running | completed | failed | cancelled
     */
    private String status;

    /**
     * 基础模型
     */
    private String baseModel;

    /**
     * 微调框架
     */
    private String provider;

    /**
     * 输出路径
     */
    private String outputPath;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 训练日志
     */
    private String logs;

    /**
     * 评估指标
     */
    private Metrics metrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metrics {
        /**
         * 训练损失
         */
        private Double trainLoss;

        /**
         * 验证损失
         */
        private Double evalLoss;

        /**
         * 训练样本数
         */
        private Integer trainSamples;

        /**
         * 训练步数
         */
        private Integer trainSteps;

        /**
         * 训练时长（秒）
         */
        private Integer trainingDuration;
    }
}
