package com.abin.mallchat.ai.rag.controller;

import com.abin.mallchat.ai.rag.domain.dto.RAGQueryRequest;
import com.abin.mallchat.ai.rag.domain.dto.StreamChunk;
import com.abin.mallchat.ai.rag.service.RAGService;
import com.abin.mallchat.ai.rag.service.StreamConnectionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流式输出控制器
 * 提供SSE (Server-Sent Events) 流式响应接口
 * 
 * @author Abin
 */
@Slf4j
@RestController
@RequestMapping("/api/stream")
public class StreamController {
    
    @Autowired
    private RAGService ragService;
    
    @Autowired
    private StreamConnectionManager connectionManager;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 心跳间隔（秒）
     */
    private static final int HEARTBEAT_INTERVAL = 30;
    
    /**
     * 连接超时时间（秒）
     */
    private static final int CONNECTION_TIMEOUT = 300;
    
    /**
     * 流式RAG查询
     * 使用SSE协议返回流式响应
     * 
     * @param request RAG查询请求
     * @return SSE流式响应
     */
    @PostMapping(value = "/rag/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamRAGQuery(@RequestBody RAGQueryRequest request) {
        log.info("收到流式RAG查询请求，问题：{}, 文档ID：{}, 用户ID：{}", 
                request.getQuestion(), request.getDocumentId(), request.getUserId());
        
        // 生成连接ID
        String connectionId = generateConnectionId(request.getUserId());
        
        // 验证请求参数
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return Flux.just(
                    ServerSentEvent.<String>builder()
                            .event("error")
                            .data(toJson(StreamChunk.error(0, "问题不能为空")))
                            .build()
            );
        }
        
        if (request.getUserId() == null) {
            return Flux.just(
                    ServerSentEvent.<String>builder()
                            .event("error")
                            .data(toJson(StreamChunk.error(0, "用户ID不能为空")))
                            .build()
            );
        }
        
        // 分块计数器
        AtomicInteger chunkIndex = new AtomicInteger(0);
        
        // 获取RAG查询的流式响应
        Flux<String> contentFlux = ragService.ragQuery(request);
        
        // 将内容流转换为StreamChunk流
        Flux<ServerSentEvent<String>> contentEvents = contentFlux
                .map(content -> {
                    // 更新连接活跃时间
                    connectionManager.updateLastActivity(connectionId);
                    
                    StreamChunk chunk = StreamChunk.content(chunkIndex.getAndIncrement(), content);
                    return ServerSentEvent.<String>builder()
                            .event("message")
                            .data(toJson(chunk))
                            .build();
                })
                .concatWith(Mono.fromCallable(() -> {
                    // 添加结束标记
                    StreamChunk endChunk = StreamChunk.end(chunkIndex.get());
                    log.info("流式响应完成，共发送 {} 个数据块", chunkIndex.get());
                    return ServerSentEvent.<String>builder()
                            .event("done")
                            .data(toJson(endChunk))
                            .build();
                }))
                .doOnError(error -> {
                    log.error("流式响应发生错误", error);
                })
                .onErrorResume(error -> {
                    // 发送错误事件
                    StreamChunk errorChunk = StreamChunk.error(chunkIndex.get(), 
                            "处理请求时发生错误：" + error.getMessage());
                    return Mono.just(
                            ServerSentEvent.<String>builder()
                                    .event("error")
                                    .data(toJson(errorChunk))
                                    .build()
                    );
                });
        
        // 添加心跳机制
        Flux<ServerSentEvent<String>> heartbeatFlux = Flux.interval(Duration.ofSeconds(HEARTBEAT_INTERVAL))
                .map(tick -> {
                    log.debug("发送心跳消息，连接ID：{}", connectionId);
                    // 更新连接活跃时间
                    connectionManager.updateLastActivity(connectionId);
                    
                    return ServerSentEvent.<String>builder()
                            .event("heartbeat")
                            .data(toJson(StreamChunk.heartbeat()))
                            .build();
                })
                .doOnCancel(() -> log.debug("心跳流被取消"));
        
        // 超时检测
        Flux<ServerSentEvent<String>> timeoutFlux = Flux.<ServerSentEvent<String>>never()
                .timeout(Duration.ofSeconds(CONNECTION_TIMEOUT))
                .onErrorResume(error -> {
                    log.warn("连接超时，关闭流，连接ID：{}", connectionId);
                    StreamChunk timeoutChunk = StreamChunk.error(chunkIndex.get(), "连接超时");
                    return Mono.just(
                            ServerSentEvent.<String>builder()
                                    .event("timeout")
                                    .data(toJson(timeoutChunk))
                                    .build()
                    );
                });
        
        // 合并内容流、心跳流和超时检测
        Flux<ServerSentEvent<String>> resultFlux = Flux.merge(contentEvents, heartbeatFlux, timeoutFlux)
                .takeUntilOther(contentEvents.filter(event -> "done".equals(event.event()) || "error".equals(event.event())).next())
                .doOnSubscribe(subscription -> {
                    // 注册连接 - 将subscription转换为Disposable
                    reactor.core.Disposable disposable = new reactor.core.Disposable() {
                        @Override
                        public void dispose() {
                            subscription.cancel();
                        }
                        
                        @Override
                        public boolean isDisposed() {
                            return false; // Subscription没有isDisposed方法
                        }
                    };
                    connectionManager.registerConnection(connectionId, request.getUserId(), disposable);
                    log.info("流式连接已建立，连接ID：{}", connectionId);
                })
                .doOnCancel(() -> {
                    log.info("客户端取消了流式连接，释放资源，连接ID：{}", connectionId);
                    connectionManager.unregisterConnection(connectionId);
                })
                .doOnComplete(() -> {
                    log.info("流式连接正常关闭，资源已释放，连接ID：{}", connectionId);
                    connectionManager.unregisterConnection(connectionId);
                })
                .doOnTerminate(() -> {
                    log.debug("流式连接终止，执行清理操作，连接ID：{}", connectionId);
                });
        
        return resultFlux;
    }
    
    /**
     * 简化版流式查询（直接返回文本流）
     * 不使用ServerSentEvent封装，直接返回文本
     * 
     * @param request RAG查询请求
     * @return 文本流
     */
    @PostMapping(value = "/rag/query/simple", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamRAGQuerySimple(@RequestBody RAGQueryRequest request) {
        log.info("收到简化版流式RAG查询请求，问题：{}, 文档ID：{}", 
                request.getQuestion(), request.getDocumentId());
        
        // 验证请求参数
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return Flux.just("错误：问题不能为空");
        }
        
        if (request.getUserId() == null) {
            return Flux.just("错误：用户ID不能为空");
        }
        
        return ragService.ragQuery(request)
                .doOnComplete(() -> log.info("简化版流式响应完成"))
                .doOnError(error -> log.error("简化版流式响应发生错误", error))
                .onErrorResume(error -> Flux.just("抱歉，处理您的问题时发生错误，请稍后重试。"));
    }
    
    /**
     * 测试SSE连接
     * 
     * @return 测试消息流
     */
    @GetMapping(value = "/test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> testSSE() {
        log.info("收到SSE测试请求");
        
        return Flux.interval(Duration.ofSeconds(1))
                .take(10)
                .map(sequence -> {
                    StreamChunk chunk = StreamChunk.content(sequence.intValue(), 
                            "测试消息 " + sequence);
                    return ServerSentEvent.<String>builder()
                            .event("message")
                            .data(toJson(chunk))
                            .build();
                })
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data(toJson(StreamChunk.end(10)))
                                .build()
                ));
    }
    
    /**
     * 将对象转换为JSON字符串
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败", e);
            return "{\"error\":\"序列化失败\"}";
        }
    }
    
    /**
     * 生成连接ID
     */
    private String generateConnectionId(Long userId) {
        return "conn_" + userId + "_" + System.currentTimeMillis();
    }
}
