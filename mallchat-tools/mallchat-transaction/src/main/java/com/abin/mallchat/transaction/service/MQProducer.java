package com.abin.mallchat.transaction.service;

import com.abin.mallchat.transaction.annotation.SecureInvoke;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Description: 发送mq工具类
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-08-12
 */
@Slf4j
public class MQProducer {

    @Autowired(required = false)
    @Lazy
    private RocketMQTemplate rocketMQTemplate;

    public void sendMsg(String topic, Object body) {
        try {
            if (rocketMQTemplate != null) {
                Message<Object> build = MessageBuilder.withPayload(body).build();
                rocketMQTemplate.send(topic, build);
                log.info("MQ消息发送成功 - topic: {}, body: {}", topic, body);
            } else {
                log.warn("RocketMQTemplate 不可用，跳过消息发送 - topic: {}, body: {}", topic, body);
            }
        } catch (Exception e) {
            log.error("MQ消息发送失败 - topic: {}, body: {}, error: {}", topic, body, e.getMessage());
        }
    }

    /**
     * 发送可靠消息，在事务提交后保证发送成功
     *
     * @param topic
     * @param body
     */
    @SecureInvoke
    public void sendSecureMsg(String topic, Object body, Object key) {
        try {
            if (rocketMQTemplate != null) {
                Message<Object> build = MessageBuilder
                        .withPayload(body)
                        .setHeader("KEYS", key)
                        .build();
                rocketMQTemplate.send(topic, build);
                log.info("MQ可靠消息发送成功 - topic: {}, body: {}, key: {}", topic, body, key);
            } else {
                log.warn("RocketMQTemplate 不可用，跳过可靠消息发送 - topic: {}, body: {}, key: {}", topic, body, key);
            }
        } catch (Exception e) {
            log.error("MQ可靠消息发送失败 - topic: {}, body: {}, key: {}, error: {}", topic, body, key, e.getMessage());
        }
    }
}
