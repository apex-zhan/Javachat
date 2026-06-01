package com.abin.mallchat.ai.vector.service.impl;

import com.abin.mallchat.ai.vector.service.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mock Embedding 服务实现
 *
 * 用于本地开发/测试环境，无需部署真实Embedding模型即可启动项目。
 * 基于文本内容生成确定性伪随机向量（相同文本产生相同向量），
 * 保证Mock模式下语义检索的一致性。
 *
 * 特性：
 * - 维度可配置（默认1024，兼容bge-large-zh-v1.5）
 * - 相同文本产生相同向量（基于MD5哈希）
 * - 支持批量生成
 *
 * 启用方式：spring.profiles.active=mock
 *
 * @author abin
 */
@Slf4j
@Service
@Profile("mock")
public class MockEmbeddingService implements EmbeddingService {

    private static final int DEFAULT_DIMENSION = 1024;
    private static final float NORMALIZATION_FACTOR = 1000.0f;

    @Override
    public float[] generateEmbedding(String text) {
        log.debug("[Mock] Generating embedding for text of length: {}", text.length());
        float[] vector = generateDeterministicVector(text, DEFAULT_DIMENSION);
        log.debug("[Mock] Generated embedding with dimension: {}", vector.length);
        return vector;
    }

    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        log.info("[Mock] Generating embeddings for {} texts (batch mode)", texts.size());
        List<float[]> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(generateEmbedding(text));
        }
        log.info("[Mock] Successfully generated {} embeddings in batch", embeddings.size());
        return embeddings;
    }

    /**
     * 基于文本内容生成确定性伪随机向量
     *
     * 算法：使用MD5哈希种子，生成高斯分布的伪随机数
     * 相同文本 -> 相同MD5 -> 相同向量
     *
     * @param text 输入文本
     * @param dimension 向量维度
     * @return 单位向量（L2范数为1）
     */
    private float[] generateDeterministicVector(String text, int dimension) {
        try {
            // 使用MD5生成确定性种子
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));

            float[] vector = new float[dimension];
            double sumSquares = 0.0;

            // 基于哈希值生成伪随机数
            for (int i = 0; i < dimension; i++) {
                // 使用多个字节组合生成种子
                int seed = (hash[i % hash.length] & 0xFF) |
                        ((hash[(i + 7) % hash.length] & 0xFF) << 8) |
                        ((hash[(i + 13) % hash.length] & 0xFF) << 16) |
                ((hash[(i + 19) % hash.length] & 0xFF) << 24);

                // 线性同余生成器（LCG）生成伪随机数
                long value = ((long) seed * 1103515245L + 12345L) & 0x7fffffffL;
                vector[i] = (float) (value / NORMALIZATION_FACTOR);
                sumSquares += vector[i] * vector[i];
            }

            // L2归一化（单位向量）
            float norm = (float) Math.sqrt(sumSquares);
            if (norm > 0) {
                for (int i = 0; i < dimension; i++) {
                    vector[i] /= norm;
                }
            }

            return vector;

        } catch (NoSuchAlgorithmException e) {
            // 降级方案：使用简单哈希
            log.warn("[Mock] MD5 not available, using fallback hash");
            return generateFallbackVector(text, dimension);
        }
    }

    private float[] generateFallbackVector(String text, int dimension) {
        float[] vector = new float[dimension];
        double sumSquares = 0.0;

        for (int i = 0; i < dimension; i++) {
            int hash = text.hashCode() + i * 31;
            vector[i] = (float) ((hash % 1000) / 1000.0);
            sumSquares += vector[i] * vector[i];
        }

        float norm = (float) Math.sqrt(sumSquares);
        if (norm > 0) {
            for (int i = 0; i < dimension; i++) {
                vector[i] /= norm;
            }
        }

        return vector;
    }
}