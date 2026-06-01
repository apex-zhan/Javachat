package com.abin.mallchat.ai.finetune.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 微调训练请求 DTO
 *
 * @author abin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FineTuneRequest {

    /**
     * 微调框架
     * llamafactory | axolotl
     */
    private String provider;

    /**
     * 基础模型名称
     * 例如：qwen2.5-14b, llama3-70b
     */
    private String baseModel;

    /**
     * 训练数据集路径
     */
    private String datasetPath;

    /**
     * 训练数据（内联方式，替代 datasetPath）
     */
    private List<Map<String, String>> trainingData;

    /**
     * LoRA 配置
     */
    private LoraConfig loraConfig;

    /**
     * 训练超参数
     */
    private TrainingConfig trainingConfig;

    /**
     * 输出目录
     */
    private String outputDir;

    /**
     * 是否使用 DeepSpeed
     */
    private Boolean useDeepSpeed;

    /**
     * DeepSpeed 配置文件路径
     */
    private String deepSpeedConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoraConfig {
        /**
         * LoRA 秩
         */
        private Integer r = 64;

        /**
         * LoRA Alpha
         */
        private Integer loraAlpha = 128;

        /**
         * LoRA Dropout
         */
        private Double loraDropout = 0.05;

        /**
         * 目标模块
         */
        private List<String> targetModules;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrainingConfig {
        /**
         * 训练轮数
         */
        private Integer numTrainEpochs = 3;

        /**
         * 批量大小
         */
        private Integer perDeviceTrainBatchSize = 1;

        /**
         * 梯度累积步数
         */
        private Integer gradientAccumulationSteps = 8;

        /**
         * 学习率
         */
        private Double learningRate = 5e-5;

        /**
         * 最大序列长度
         */
        private Integer maxSeqLength = 2048;

        /**
         *  warmup 比例
         */
        private Double warmupRatio = 0.03;

        /**
         * 学习率调度器
         */
        private String lrSchedulerType = "cosine";

        /**
         * 是否使用 FP16
         */
        private Boolean fp16 = true;

        /**
         * 是否使用 BF16
         */
        private Boolean bf16 = false;

        /**
         * 日志间隔
         */
        private Integer loggingSteps = 10;

        /**
         * 保存间隔
         */
        private Integer saveSteps = 100;
    }
}
