package com.abin.mallchat.ai.finetune.service;

import com.abin.mallchat.ai.finetune.client.FineTuneClient;
import com.abin.mallchat.ai.finetune.config.FineTuneConfig;
import com.abin.mallchat.ai.finetune.dto.FineTuneRequest;
import com.abin.mallchat.ai.finetune.dto.FineTuneResponse;
import com.abin.mallchat.ai.finetune.dto.FineTuneStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 微调服务
 *
 * 提供微调任务的提交、查询、取消等功能
 *
 * @author abin
 */
@Slf4j
@Service
public class FineTuneService {

    @Autowired
    private FineTuneClient fineTuneClient;

    @Autowired
    private FineTuneConfig fineTuneConfig;

    /**
     * 提交微调任务（使用默认配置）
     *
     * @param baseModel 基础模型
     * @param datasetPath 数据集路径
     * @return 微调任务响应
     */
    public Mono<FineTuneResponse> submitFineTune(String baseModel, String datasetPath) {
        log.info("Submitting fine-tune task, model: {}, dataset: {}", baseModel, datasetPath);

        FineTuneRequest request = FineTuneRequest.builder()
                .provider(fineTuneConfig.getProvider())
                .baseModel(baseModel)
                .datasetPath(datasetPath)
                .loraConfig(FineTuneRequest.LoraConfig.builder()
                        .r(64)
                        .loraAlpha(128)
                        .loraDropout(0.05)
                        .build())
                .trainingConfig(FineTuneRequest.TrainingConfig.builder()
                        .numTrainEpochs(3)
                        .perDeviceTrainBatchSize(1)
                        .gradientAccumulationSteps(8)
                        .learningRate(5e-5)
                        .maxSeqLength(2048)
                        .build())
                .build();

        return fineTuneClient.submitFineTune(request);
    }

    /**
     * 提交微调任务（自定义配置）
     *
     * @param request 微调请求
     * @return 微调任务响应
     */
    public Mono<FineTuneResponse> submitFineTune(FineTuneRequest request) {
        log.info("Submitting fine-tune task with custom config, model: {}", request.getBaseModel());
        return fineTuneClient.submitFineTune(request);
    }

    /**
     * 查询微调任务状态
     */
    public Mono<FineTuneStatusResponse> getTaskStatus(String taskId) {
        log.debug("Getting fine-tune task status: {}", taskId);
        return fineTuneClient.getTaskStatus(taskId);
    }

    /**
     * 取消微调任务
     */
    public Mono<Void> cancelTask(String taskId) {
        log.info("Cancelling fine-tune task: {}", taskId);
        return fineTuneClient.cancelTask(taskId);
    }

    /**
     * 获取微调任务日志
     */
    public Mono<String> getTaskLogs(String taskId, Integer lines) {
        log.debug("Getting fine-tune task logs: {}", taskId);
        return fineTuneClient.getTaskLogs(taskId, lines);
    }

    /**
     * 获取微调后的模型列表
     */
    public Mono<List<FineTuneResponse>> listFineTunedModels() {
        log.debug("Listing fine-tuned models");
        return fineTuneClient.listModels();
    }

    /**
     * 检查微调服务健康状态
     */
    public Mono<Boolean> isServiceHealthy() {
        return fineTuneClient.healthCheck();
    }

    /**
     * 使用 LLaMA-Factory 进行微调（推荐）
     */
    public Mono<FineTuneResponse> fineTuneWithLlamaFactory(String baseModel, String datasetPath) {
        log.info("Submitting LLaMA-Factory fine-tune task, model: {}", baseModel);

        FineTuneRequest request = FineTuneRequest.builder()
                .provider("llamafactory")
                .baseModel(baseModel)
                .datasetPath(datasetPath)
                .loraConfig(FineTuneRequest.LoraConfig.builder()
                        .r(64)
                        .loraAlpha(128)
                        .loraDropout(0.05)
                        .build())
                .trainingConfig(FineTuneRequest.TrainingConfig.builder()
                        .numTrainEpochs(3)
                        .perDeviceTrainBatchSize(1)
                        .gradientAccumulationSteps(8)
                        .learningRate(5e-5)
                        .maxSeqLength(2048)
                        .build())
                .build();

        return fineTuneClient.submitFineTune(request);
    }

    /**
     * 使用 Axolotl 进行微调（备选）
     */
    public Mono<FineTuneResponse> fineTuneWithAxolotl(String baseModel, String datasetPath) {
        log.info("Submitting Axolotl fine-tune task, model: {}", baseModel);

        FineTuneRequest request = FineTuneRequest.builder()
                .provider("axolotl")
                .baseModel(baseModel)
                .datasetPath(datasetPath)
                .loraConfig(FineTuneRequest.LoraConfig.builder()
                        .r(32)
                        .loraAlpha(64)
                        .loraDropout(0.05)
                        .build())
                .trainingConfig(FineTuneRequest.TrainingConfig.builder()
                        .numTrainEpochs(3)
                        .perDeviceTrainBatchSize(2)
                        .gradientAccumulationSteps(4)
                        .learningRate(2e-4)
                        .maxSeqLength(4096)
                        .build())
                .build();

        return fineTuneClient.submitFineTune(request);
    }
}
