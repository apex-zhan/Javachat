package com.abin.mallchat.common.user.service.impl;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Description: WebSocket推送监控指标服务
 * <p>
 * 使用Micrometer + Prometheus暴露以下指标：
 * - mallchat_ws_online_connections (Gauge) 当前在线连接数
 * - mallchat_ws_push_total (Counter) 总推送次数
 * - mallchat_ws_push_latency (Timer) 推送延迟
 * - mallchat_ws_ack_timeout (Counter) ACK超时次数
 * - mallchat_offline_msg_queue_size (Gauge) 离线消息队列总长度
 *
 * Date: 2026-05-11
 */
@Slf4j
@Service
public class WsMetricsService {

    @Autowired
    private MeterRegistry meterRegistry;

    private AtomicLong onlineConnections = new AtomicLong(0);
    private AtomicLong offlineMsgQueueSize = new AtomicLong(0);

    private Counter pushSuccessCounter;
    private Counter pushFailedCounter;
    private Counter ackTimeoutCounter;
    private Timer pushLatencyTimer;

    @PostConstruct
    public void init() {
        // 在线连接数 Gauge
        Gauge.builder("mallchat_ws_online_connections", onlineConnections, AtomicLong::get)
                .description("当前WebSocket在线连接数")
                .register(meterRegistry);

        // 离线消息队列长度 Gauge
        Gauge.builder("mallchat_offline_msg_queue_size", offlineMsgQueueSize, AtomicLong::get)
                .description("离线消息队列总长度")
                .register(meterRegistry);

        // 推送成功 Counter
        pushSuccessCounter = Counter.builder("mallchat_ws_push_total")
                .tag("result", "success")
                .description("WebSocket推送成功次数")
                .register(meterRegistry);

        // 推送失败 Counter
        pushFailedCounter = Counter.builder("mallchat_ws_push_total")
                .tag("result", "failed")
                .description("WebSocket推送失败次数")
                .register(meterRegistry);

        // ACK超时 Counter
        ackTimeoutCounter = Counter.builder("mallchat_ws_ack_timeout")
                .description("消息ACK超时次数")
                .register(meterRegistry);

        // 推送延迟 Timer
        pushLatencyTimer = Timer.builder("mallchat_ws_push_latency")
                .description("WebSocket推送延迟")
                .register(meterRegistry);

        log.info("WebSocket监控指标初始化完成");
    }

    /**
     * 更新在线连接数
     */
    public void setOnlineConnections(long count) {
        onlineConnections.set(count);
    }

    /**
     * 记录推送成功
     */
    public void recordPushSuccess() {
        pushSuccessCounter.increment();
    }

    /**
     * 记录推送失败
     */
    public void recordPushFailed() {
        pushFailedCounter.increment();
    }

    /**
     * 记录推送延迟
     */
    public void recordPushLatency(long millis) {
        pushLatencyTimer.record(java.time.Duration.ofMillis(millis));
    }

    /**
     * 记录ACK超时
     */
    public void recordAckTimeout() {
        ackTimeoutCounter.increment();
    }

    /**
     * 更新离线消息队列大小
     */
    public void setOfflineMsgQueueSize(long size) {
        offlineMsgQueueSize.set(size);
    }
}
