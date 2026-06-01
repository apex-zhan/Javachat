package com.abin.mallchat.ai.llm.service;

import com.abin.mallchat.ai.llm.domain.LLMOptions;
import dev.langchain4j.data.message.ChatMessage;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * LLM Service Interface
 * 封装大语言模型调用逻辑
 *
 * @author abin
 */
public interface LLMService {

    /**
     * 流式调用 LLM
     *
     * @param prompt 提示词
     * @param options 调用选项（温度、最大 token 等）
     * @return 流式响应
     */
    Flux<String> streamChat(String prompt, LLMOptions options);

    /**
     * 非流式调用 LLM
     *
     * @param prompt 提示词
     * @param options 调用选项
     * @return 完整响应
     */
    String chat(String prompt, LLMOptions options);

    /**
     * 多轮对话流式调用 LLM
     *
     * @param messages 对话消息列表（包含历史上下文）
     * @param options 调用选项
     * @return 流式响应
     */
    Flux<String> streamChat(List<ChatMessage> messages, LLMOptions options);

    /**
     * 多轮对话非流式调用 LLM
     *
     * @param messages 对话消息列表（包含历史上下文）
     * @param options 调用选项
     * @return 完整响应
     */
    String chat(List<ChatMessage> messages, LLMOptions options);

    /**
     * 计算 token 数量
     *
     * @param text 文本内容
     * @return token 数量
     */
    int countTokens(String text);
}
