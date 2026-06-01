package com.abin.mallchat.ai.finetune.client;

import com.abin.mallchat.ai.finetune.dto.FineTuneRequest;
import com.abin.mallchat.ai.finetune.dto.FineTuneResponse;
import com.abin.mallchat.ai.finetune.dto.FineTuneStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock 微调服务客户端
 *
 * 用于本地开发/测试环境，无需部署 Python 微调服务即可启动项目。
 * 返回模拟的微调任务响应，任务状态存储在内存中。
 *
 * 启用方式：spring.profiles.active=mock
 *
 * @author abin
 */
@Slf4j
@Component
@Profile("mock")
public class MockFineTuneClient extends FineTuneClient {

    private final ConcurrentHashMap<String, MockTask> tasks = new ConcurrentHashMap<>();

    @PostConstruct
    @Override
    public void init() {
        log.info("[Mock] MockFineTuneClient initialized");
        log.warn("[Mock] 微调服务为模拟实现，所有任务返回模拟数据！");
    }

    @Override
    public Mono<FineTuneResponse> submitFineTune(FineTuneRequest request) {
        String taskId = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[Mock] Submitting fine-tune task: {}, model: {}", taskId, request.getBaseModel());

        MockTask task = new MockTask(taskId, request.getBaseModel(), request.getProvider());
        tasks.put(taskId, task);

        // 模拟异步训练过程
        new Thread(() -> simulateTraining(task)).start();

        return Mono.just(FineTuneResponse.builder()
                .taskId(taskId)
                .status("pending")
                .baseModel(request.getBaseModel())
                .provider(request.getProvider())
                .outputPath("/mock/outputs/" + taskId)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    public Mono<FineTuneStatusResponse> getTaskStatus(String taskId) {
        MockTask task = tasks.get(taskId);
        if (task == null) {
            return Mono.error(new RuntimeException("Task not found: " + taskId));
        }

        return Mono.just(FineTuneStatusResponse.builder()
                .taskId(taskId)
                .status(task.getStatus())
                .progress(task.getProgress())
                .progressDetail(task.getProgress() + "%")
                .createdAt(task.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .latestLog("[Mock] " + task.getLatestLog())
                .build());
    }

    @Override
    public Mono<Void> cancelTask(String taskId) {
        MockTask task = tasks.get(taskId);
        if (task != null) {
            task.setStatus("cancelled");
            log.info("[Mock] Task cancelled: {}", taskId);
        }
        return Mono.empty();
    }

    @Override
    public Mono<String> getTaskLogs(String taskId, Integer lines) {
        MockTask task = tasks.get(taskId);
        if (task == null) {
            return Mono.error(new RuntimeException("Task not found: " + taskId));
        }
        return Mono.just("[Mock] 这是模拟的训练日志...\n" + task.getLatestLog());
    }

    @Override
    public Mono<List<FineTuneResponse>> listModels() {
        List<FineTuneResponse> models = Arrays.asList(
                FineTuneResponse.builder()
                        .taskId("MOCK-DEMO-01")
                        .status("completed")
                        .baseModel("qwen2.5:14b")
                        .provider("llamafactory")
                        .outputPath("/mock/outputs/MOCK-DEMO-01")
                        .createdAt(LocalDateTime.now().minusDays(1))
                        .build()
        );
        return Mono.just(models);
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.just(true);
    }

    /**
     * 模拟训练过程
     */
    private void simulateTraining(MockTask task) {
        try {
            task.setStatus("running");
            String[] logs = {
                    "Loading model...",
                    "Preparing dataset...",
                    "Starting training epoch 1/3...",
                    "Epoch 1 completed, loss: 2.345",
                    "Starting training epoch 2/3...",
                    "Epoch 2 completed, loss: 1.876",
                    "Starting training epoch 3/3...",
                    "Epoch 3 completed, loss: 1.234",
                    "Saving adapter weights...",
                    "Training completed!"
            };

            for (int i = 0; i < logs.length; i++) {
                Thread.sleep(500); // 模拟训练耗时
                task.setLatestLog(logs[i]);
                task.setProgress((i + 1) * 100 / logs.length);
            }

            task.setStatus("completed");
            log.info("[Mock] Training completed for task: {}", task.getTaskId());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.setStatus("failed");
        }
    }

    // ==================== Inner Class ====================

    private static class MockTask {
        private final String taskId;
        private final String baseModel;
        private final String provider;
        private final LocalDateTime createdAt;
        private volatile String status;
        private volatile int progress;
        private volatile String latestLog;

        MockTask(String taskId, String baseModel, String provider) {
            this.taskId = taskId;
            this.baseModel = baseModel;
            this.provider = provider;
            this.createdAt = LocalDateTime.now();
            this.status = "pending";
            this.progress = 0;
            this.latestLog = "Task created";
        }

        String getTaskId() { return taskId; }
        String getBaseModel() { return baseModel; }
        String getProvider() { return provider; }
        LocalDateTime getCreatedAt() { return createdAt; }
        String getStatus() { return status; }
        void setStatus(String status) { this.status = status; }
        int getProgress() { return progress; }
        void setProgress(int progress) { this.progress = progress; }
        String getLatestLog() { return latestLog; }
        void setLatestLog(String latestLog) { this.latestLog = latestLog; }
    }
}