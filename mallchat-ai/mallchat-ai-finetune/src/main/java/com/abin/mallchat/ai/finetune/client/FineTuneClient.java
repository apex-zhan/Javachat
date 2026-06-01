package com.abin.mallchat.ai.finetune.client;

import com.abin.mallchat.ai.finetune.config.FineTuneConfig;
import com.abin.mallchat.ai.finetune.dto.FineTuneRequest;
import com.abin.mallchat.ai.finetune.dto.FineTuneResponse;
import com.abin.mallchat.ai.finetune.dto.FineTuneStatusResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import javax.annotation.PostConstruct;
import java.time.Duration;

/**
 * 微调服务 HTTP 客户端
 *
 * 负责与 Python 微调服务通信
 *
 * @author abin
 */
@Slf4j
@Component
@Profile("!mock")
public class FineTuneClient {

    @Autowired
    private FineTuneConfig fineTuneConfig;

    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        log.info("Initializing FineTuneClient, service URL: {}", fineTuneConfig.getServiceUrl());

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(fineTuneConfig.getTimeout()));

        this.webClient = WebClient.builder()
                .baseUrl(fineTuneConfig.getServiceUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 提交微调任务
     */
    public Mono<FineTuneResponse> submitFineTune(FineTuneRequest request) {
        log.info("Submitting fine-tune task, model: {}, provider: {}",
                request.getBaseModel(), request.getProvider());

        return webClient.post()
                .uri("/api/v1/finetune")
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Fine-tune submission failed: {}", errorBody);
                                    return Mono.error(new RuntimeException("Fine-tune submission failed: " + errorBody));
                                })
                )
                .bodyToMono(FineTuneResponse.class)
                .doOnSuccess(response -> log.info("Fine-tune task submitted, taskId: {}", response.getTaskId()))
                .doOnError(error -> log.error("Failed to submit fine-tune task", error));
    }

    /**
     * 查询微调任务状态
     */
    public Mono<FineTuneStatusResponse> getTaskStatus(String taskId) {
        log.debug("Querying fine-tune task status: {}", taskId);

        return webClient.get()
                .uri("/api/v1/finetune/{taskId}/status", taskId)
                .retrieve()
                .onStatus(
                        status -> status == HttpStatus.NOT_FOUND,
                        response -> Mono.error(new RuntimeException("Task not found: " + taskId))
                )
                .onStatus(
                        status -> status.isError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("Query failed: " + errorBody)))
                )
                .bodyToMono(FineTuneStatusResponse.class)
                .doOnError(error -> log.error("Failed to query task status: {}", taskId, error));
    }

    /**
     * 取消微调任务
     */
    public Mono<Void> cancelTask(String taskId) {
        log.info("Cancelling fine-tune task: {}", taskId);

        return webClient.post()
                .uri("/api/v1/finetune/{taskId}/cancel", taskId)
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("Cancel failed: " + errorBody)))
                )
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Fine-tune task cancelled: {}", taskId))
                .doOnError(error -> log.error("Failed to cancel task: {}", taskId, error));
    }

    /**
     * 获取微调任务日志
     */
    public Mono<String> getTaskLogs(String taskId, Integer lines) {
        log.debug("Querying fine-tune task logs: {}, lines: {}", taskId, lines);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/finetune/{taskId}/logs")
                        .queryParamIfPresent("lines", java.util.Optional.ofNullable(lines))
                        .build(taskId))
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(error -> log.error("Failed to query task logs: {}", taskId, error));
    }

    /**
     * 获取微调后的模型列表
     */
    public Mono<java.util.List<FineTuneResponse>> listModels() {
        log.debug("Listing fine-tuned models");

        return webClient.get()
                .uri("/api/v1/models")
                .retrieve()
                .bodyToMono(new com.fasterxml.jackson.core.type.TypeReference<java.util.List<FineTuneResponse>>() {})
                .doOnError(error -> log.error("Failed to list models", error));
    }

    /**
     * 健康检查
     */
    public Mono<Boolean> healthCheck() {
        return webClient.get()
                .uri("/health")
                .retrieve()
                .toBodilessEntity()
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .onErrorReturn(false)
                .doOnSuccess(healthy -> log.debug("Fine-tune service health: {}", healthy));
    }
}
