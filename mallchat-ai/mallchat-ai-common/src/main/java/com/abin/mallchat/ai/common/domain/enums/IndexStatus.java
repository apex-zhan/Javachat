package com.abin.mallchat.ai.common.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 索引状态枚举
 * 
 * @author zxw
 */
@Getter
@AllArgsConstructor
public enum IndexStatus {
    
    /**
     * 待索引
     */
    PENDING("PENDING", "待索引"),
    
    /**
     * 索引中
     */
    INDEXING("INDEXING", "索引中"),
    
    /**
     * 索引完成
     */
    COMPLETED("COMPLETED", "索引完成"),
    
    /**
     * 索引失败
     */
    FAILED("FAILED", "索引失败");
    
    private final String code;
    private final String desc;
}
