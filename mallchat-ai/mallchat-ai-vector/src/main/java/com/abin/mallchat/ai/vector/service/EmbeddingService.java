package com.abin.mallchat.ai.vector.service;

import java.util.List;

/**
 * Embedding 服务接口
 * 负责将文本转换为向量表示
 * 
 * @author abin
 */
public interface EmbeddingService {
    
    /**
     * 生成单个文本的向量
     * 
     * @param text 文本内容
     * @return 向量数组
     */
    float[] generateEmbedding(String text);
    
    /**
     * 批量生成文本向量
     * 
     * @param texts 文本列表
     * @return 向量列表
     */
    List<float[]> generateEmbeddings(List<String> texts);
}
