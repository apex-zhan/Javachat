package com.abin.mallchat.ai.rag.service.impl;

import com.abin.mallchat.ai.rag.domain.dto.DocumentIndexingMessage;
import com.abin.mallchat.ai.rag.service.DocumentIndexingProducer;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 文档索引消息生产者实现
 * 
 * @author zxw
 */
@Slf4j
@Service
public class DocumentIndexingProducerImpl implements DocumentIndexingProducer {
    
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    @Value("${rocketmq.ai.document-indexing-topic:MALLCHAT_AI_DOCUMENT_INDEXING}")
    private String indexingTopic;
    
    @Override
    public void sendIndexingTask(DocumentIndexingMessage message) {
        try {
            log.info("发送文档索引任务，文档ID：{}, 标题：{}", message.getDocumentId(), message.getTitle());
            
            // 发送异步消息
            rocketMQTemplate.asyncSend(indexingTopic, message, new org.apache.rocketmq.client.producer.SendCallback() {
                @Override
                public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                    log.info("文档索引任务发送成功，文档ID：{}, MessageId：{}", 
                            message.getDocumentId(), sendResult.getMsgId());
                }
                
                @Override
                public void onException(Throwable e) {
                    log.error("文档索引任务发送失败，文档ID：{}", message.getDocumentId(), e);
                }
            });
            
        } catch (Exception e) {
            log.error("发送文档索引任务异常，文档ID：{}", message.getDocumentId(), e);
            throw new RuntimeException("发送索引任务失败", e);
        }
    }
}
