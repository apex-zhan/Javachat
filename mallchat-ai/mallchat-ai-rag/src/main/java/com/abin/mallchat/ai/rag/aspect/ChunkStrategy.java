package com.abin.mallchat.ai.rag.aspect;

/**
 * 文档分块策略枚举
 */
public enum ChunkStrategy {
    
    /**
     * 固定长度分块
     * 适用于通用文本，无明显结构
     */
    FIXED_SIZE,
    
    /**
     * 语义分块
     * 适用于结构化文档（Markdown、HTML）
     * 按段落、标题、列表分块
     */
    SEMANTIC,
    
    /**
     * 递归分块
     * 适用于代码、嵌套结构
     * 按层级递归切分
     */
    RECURSIVE,
    
    /**
     * 自动选择
     * 根据文档类型自动选择最佳策略
     */
    AUTO
}
