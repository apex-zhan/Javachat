package com.abin.mallchat.ai.rag.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式响应数据块
 * 用于封装SSE流式输出的每个数据片段
 * 
 * @author zxw
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamChunk {
    
    /**
     * 分块序号（从0开始）
     */
    private Integer index;
    
    /**
     * 内容片段
     */
    private String content;
    
    /**
     * 是否结束（true表示流结束）
     */
    private Boolean finished;
    
    /**
     * 错误信息（如有）
     */
    private String error;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 创建内容块
     */
    public static StreamChunk content(Integer index, String content) {
        return StreamChunk.builder()
                .index(index)
                .content(content)
                .finished(false)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建结束标记
     */
    public static StreamChunk end(Integer index) {
        return StreamChunk.builder()
                .index(index)
                .content("")
                .finished(true)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建错误块
     */
    public static StreamChunk error(Integer index, String error) {
        return StreamChunk.builder()
                .index(index)
                .content("")
                .finished(true)
                .error(error)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 创建心跳块
     */
    public static StreamChunk heartbeat() {
        return StreamChunk.builder()
                .index(-1)
                .content("")
                .finished(false)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
