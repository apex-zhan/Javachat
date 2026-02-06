package com.abin.mallchat.ai.assistant.service.impl;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: ai-assistant-rag, Property 2: Context Window Compliance
 * 
 * 验证处理后的内容在上下文窗口限制内，且重要信息被保留
 *
 * @author zxw
 * @since 2025-01-07
 */
public class ContextWindowCompliancePropertyTest {

    private OpenAiTokenizer tokenizer;
    private static final String SYSTEM_PROMPT = "你是一个专业的聊天内容总结助手。";

    @BeforeEach
    void setUp() {
        tokenizer = new OpenAiTokenizer("gpt-3.5-turbo");
    }

    /**
     * Property 2: 截断后的内容token数应该在限制范围内
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 2: Context Window Compliance - Token Limit")
    void truncatedContentShouldBeWithinTokenLimit(
            @ForAll @StringLength(min = 1000, max = 50000) String longContent,
            @ForAll @IntRange(min = 500, max = 4096) int maxTokens
    ) {
        // Given: 一个可能超出限制的长内容
        String content = longContent;

        // When: 检查并截断内容
        String truncated = truncateContentIfNeeded(content, maxTokens);

        // Then: 截断后的内容应该在token限制内
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage(truncated));

        int tokenCount = tokenizer.estimateTokenCountInMessages(messages);
        assertThat(tokenCount).isLessThanOrEqualTo(maxTokens);
    }

    /**
     * Property 2: 如果原内容在限制内，不应该被截断
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 2: Context Window Compliance - No Truncation")
    void shortContentShouldNotBeTruncated(
            @ForAll @StringLength(min = 10, max = 500) String shortContent,
            @ForAll @IntRange(min = 2000, max = 4096) int maxTokens
    ) {
        // Given: 一个短内容
        String content = shortContent;

        // When: 检查并截断内容
        String result = truncateContentIfNeeded(content, maxTokens);

        // Then: 内容应该保持不变（不被截断）
        assertThat(result).isEqualTo(content);
    }

    /**
     * Property 2: 截断后的内容应该保留开头部分（重要信息通常在开头）
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 2: Context Window Compliance - Preserve Beginning")
    void truncatedContentShouldPreserveBeginning(
            @ForAll @StringLength(min = 1000, max = 10000) String longContent,
            @ForAll @IntRange(min = 500, max = 2000) int maxTokens
    ) {
        // Given: 一个长内容，确保开头有特定标记
        String marker = "[IMPORTANT_START]";
        String content = marker + longContent;

        // When: 截断内容
        String truncated = truncateContentIfNeeded(content, maxTokens);

        // Then: 截断后的内容应该包含开头的标记
        assertThat(truncated).contains(marker);
    }

    /**
     * Property 2: 截断标记应该被添加到截断的内容中
     */
    @Property(tries = 100)
    @Label("Feature: ai-assistant-rag, Property 2: Context Window Compliance - Truncation Marker")
    void truncatedContentShouldHaveMarker(
            @ForAll @StringLength(min = 5000, max = 50000) String veryLongContent,
            @ForAll @IntRange(min = 500, max = 1000) int smallMaxTokens
    ) {
        // Given: 一个很长的内容和较小的token限制
        String content = veryLongContent;

        // When: 截断内容
        String truncated = truncateContentIfNeeded(content, smallMaxTokens);

        // Then: 如果内容被截断，应该包含截断标记
        List<ChatMessage> originalMessages = new ArrayList<>();
        originalMessages.add(new SystemMessage(SYSTEM_PROMPT));
        originalMessages.add(new UserMessage(content));
        int originalTokens = tokenizer.estimateTokenCountInMessages(originalMessages);

        if (originalTokens > smallMaxTokens) {
            assertThat(truncated).contains("[注：由于内容过长，部分聊天记录已被截断]");
        }
    }

    // ========== Helper Methods ==========

    /**
     * 模拟AIAssistantServiceImpl中的截断逻辑
     */
    private String truncateContentIfNeeded(String content, int maxTokens) {
        int reservedTokens = 500;
        int availableTokens = maxTokens - reservedTokens;

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage(content));

        int tokenCount = tokenizer.estimateTokenCountInMessages(messages);

        if (tokenCount <= maxTokens) {
            return content;
        }

        // 简单截断策略：按字符比例截断
        double ratio = (double) availableTokens / tokenCount;
        int targetLength = (int) (content.length() * ratio * 0.9); // 0.9是安全系数

        String truncated = content.substring(0, Math.min(targetLength, content.length()));
        truncated += "\n\n[注：由于内容过长，部分聊天记录已被截断]";

        return truncated;
    }
}
