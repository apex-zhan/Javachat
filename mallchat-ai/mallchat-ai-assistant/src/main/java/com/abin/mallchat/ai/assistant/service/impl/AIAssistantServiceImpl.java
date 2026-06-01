package com.abin.mallchat.ai.assistant.service.impl;

import com.abin.mallchat.ai.assistant.domain.dto.QuestionRequest;
import com.abin.mallchat.ai.assistant.service.AIAssistantService;
import com.abin.mallchat.ai.common.dao.AIConversationDao;
import com.abin.mallchat.ai.common.domain.entity.AIConversation;
import com.abin.mallchat.ai.common.domain.enums.ConversationType;
import com.abin.mallchat.ai.llm.domain.LLMOptions;
import com.abin.mallchat.ai.llm.service.LLMService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI助手服务实现
 *
 * @author zxw
 * @since 2025-01-07
 */
@Slf4j
@Service
public class AIAssistantServiceImpl implements AIAssistantService {

    @Autowired
    private LLMService llmService;

    @Autowired
    private AIConversationDao aiConversationDao;

    @Autowired(required = false)
    private OpenAiTokenizer tokenizer;

    @Value("${langchain4j.open-ai.chat-model.max-tokens:4096}")
    private Integer defaultMaxTokens;

    @Value("${ai.assistant.qa.system-prompt:你是一个智能助手，请根据用户的问题提供准确、有帮助的回答。}")
    private String qaSystemPrompt;

    @Override
    public Flux<String> answerQuestion(QuestionRequest request) {
        log.info("开始处理智能问答，用户ID: {}, 问题: {}", request.getUserId(), request.getQuestion());

        long startTime = System.currentTimeMillis();
        AtomicReference<String> fullResponse = new AtomicReference<>("");

        // 生成或获取会话ID
        final String sessionId;
        final boolean isNewSession;
        if (request.getConversationId() == null || request.getConversationId().trim().isEmpty()) {
            sessionId = generateSessionId();
            isNewSession = true;
            log.info("创建新会话，sessionId: {}, 用户ID: {}", sessionId, request.getUserId());
        } else {
            sessionId = request.getConversationId();
            isNewSession = false;
            log.info("继续会话，sessionId: {}, 用户ID: {}", sessionId, request.getUserId());
        }
        final String finalSessionId = sessionId;

        try {
            // 1. 验证问题内容合法性
            if (!validateQuestion(request.getQuestion())) {
                return Flux.just("抱歉，您的问题包含不合法的内容，请重新提问。");
            }

            // 2. 构造对话消息列表（支持多轮对话上下文）
            List<ChatMessage> messages = buildConversationMessages(request, finalSessionId);

            // 3. 调用LLM流式生成回答
            LLMOptions options = LLMOptions.builder()
                    .temperature(0.8)
                    .maxTokens(2000)
                    .build();

            return llmService.streamChat(messages, options)
                    .doOnNext(chunk -> {
                        // 累积完整响应
                        fullResponse.updateAndGet(current -> current + chunk);
                    })
                    .doOnComplete(() -> {
                        // 4. 保存对话历史到数据库
                        long responseTime = System.currentTimeMillis() - startTime;
                        saveConversationHistory(request, finalSessionId, fullResponse.get(), responseTime);
                        log.info("智能问答完成，用户ID: {}, sessionId: {}, 耗时: {}ms, 新会话: {}",
                                request.getUserId(), finalSessionId, responseTime, isNewSession);
                    })
                    .doOnError(e -> {
                        log.error("智能问答失败，用户ID: {}, sessionId: {}", request.getUserId(), finalSessionId, e);
                        // 即使失败也保存记录
                        saveConversationHistory(request, finalSessionId, "ERROR: " + e.getMessage(),
                                System.currentTimeMillis() - startTime);
                    });

        } catch (Exception e) {
            log.error("处理智能问答时发生错误", e);
            return Flux.just("抱歉，处理您的问题时发生错误，请稍后再试。");
        }
    }

    /**
     * 验证问题内容合法性
     */
    private boolean validateQuestion(String question) {
        if (question == null || question.trim().isEmpty()) {
            return false;
        }

        // 基本的内容安全检查
        String lowerQuestion = question.toLowerCase();
        String[] forbiddenWords = {"hack", "attack", "exploit", "inject"};

        for (String word : forbiddenWords) {
            if (lowerQuestion.contains(word)) {
                log.warn("问题包含敏感词: {}", word);
                return false;
            }
        }

        return true;
    }

    /**
     * 生成唯一会话ID
     */
    private String generateSessionId() {
        return "conv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 构造多轮对话消息列表
     * 包含系统提示 + 历史对话 + 当前问题
     */
    private List<ChatMessage> buildConversationMessages(QuestionRequest request, String sessionId) {
        List<ChatMessage> messages = new ArrayList<>();

        // 1. 添加系统提示
        messages.add(new SystemMessage(qaSystemPrompt));

        // 2. 如果有上下文信息，作为补充背景
        if (request.getContext() != null && !request.getContext().trim().isEmpty()) {
            messages.add(new UserMessage("背景信息：" + request.getContext()));
            messages.add(new AiMessage("明白了，我会结合这些背景信息来回答您的问题。"));
        }

        // 3. 加载历史对话记录（多轮对话上下文）
        List<AIConversation> history = loadConversationHistory(sessionId);
        for (AIConversation conv : history) {
            if (conv.getUserInput() != null && !conv.getUserInput().isEmpty()) {
                messages.add(new UserMessage(conv.getUserInput()));
            }
            if (conv.getAiResponse() != null && !conv.getAiResponse().isEmpty()
                    && !conv.getAiResponse().startsWith("ERROR:")) {
                messages.add(new AiMessage(conv.getAiResponse()));
            }
        }

        // 4. 添加当前问题
        messages.add(new UserMessage(request.getQuestion()));

        // 5. 检查token数量，如果超出限制则截断历史
        int maxTokens = defaultMaxTokens != null ? defaultMaxTokens : 4096;
        messages = truncateMessagesIfNeeded(messages, maxTokens);

        log.info("构建对话消息列表完成，共 {} 条消息，sessionId: {}", messages.size(), sessionId);
        return messages;
    }

    /**
     * 加载指定会话的历史对话记录
     */
    private List<AIConversation> loadConversationHistory(String sessionId) {
        try {
            LambdaQueryWrapper<AIConversation> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AIConversation::getSessionId, sessionId)
                    .eq(AIConversation::getConversationType, ConversationType.QA.name())
                    .orderByAsc(AIConversation::getCreateTime)
                    .last("LIMIT 20"); // 最多加载最近20轮对话

            return aiConversationDao.list(wrapper);
        } catch (Exception e) {
            log.error("加载对话历史失败，sessionId: {}", sessionId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 检查消息列表token数量，超出限制时截断历史消息
     */
    private List<ChatMessage> truncateMessagesIfNeeded(List<ChatMessage> messages, int maxTokens) {
        // Mock模式下tokenizer可能为空，使用简单的字符数估算
        int tokenCount = estimateTokenCountInMessages(messages);

        if (tokenCount <= maxTokens) {
            return messages;
        }

        log.warn("对话消息token数量: {} 超出限制: {}，需要截断历史", tokenCount, maxTokens);

        // 保留系统提示和最近的消息，移除较早的历史
        List<ChatMessage> truncated = new ArrayList<>();
        truncated.add(messages.get(0)); // 保留系统提示

        // 从后往前添加消息，直到接近限制
        int currentTokens = estimateTokenCountInMessages(List.of(messages.get(0)));
        for (int i = messages.size() - 1; i > 0; i--) {
            ChatMessage msg = messages.get(i);
            int msgTokens = estimateMessageTokens(msg);
            if (currentTokens + msgTokens < maxTokens * 0.8) { // 保留20%余量
                truncated.add(msg);
                currentTokens += msgTokens;
            } else {
                break;
            }
        }

        // 重新排序（系统提示在前，然后是按时间顺序的消息）
        List<ChatMessage> result = new ArrayList<>();
        result.add(truncated.get(0)); // 系统提示
        for (int i = truncated.size() - 1; i > 0; i--) {
            result.add(truncated.get(i));
        }

        log.info("截断后消息数量: {}, 估计token: {}", result.size(), currentTokens);
        return result;
    }

    /**
     * 估算消息列表的token数量
     * 优先使用OpenAiTokenizer，不可用则使用字符数估算
     */
    private int estimateTokenCountInMessages(List<ChatMessage> messages) {
        if (tokenizer != null) {
            return tokenizer.estimateTokenCountInMessages(messages);
        }
        // Mock模式降级：简单估算（中文字符约1.5字符/token，英文约4字符/token）
        int totalChars = messages.stream()
                .mapToInt(msg -> {
                    if (msg instanceof SystemMessage) return ((SystemMessage) msg).text().length();
                    else if (msg instanceof UserMessage) return ((UserMessage) msg).text().length();
                    else if (msg instanceof AiMessage) return ((AiMessage) msg).text().length();
                    return 0;
                }).sum();
        return totalChars / 2; // 粗略估算
    }

    /**
     * 估算单条消息的token数量
     */
    private int estimateMessageTokens(ChatMessage message) {
        if (tokenizer != null) {
            if (message instanceof SystemMessage) {
                return tokenizer.estimateTokenCountInText(((SystemMessage) message).text());
            } else if (message instanceof UserMessage) {
                return tokenizer.estimateTokenCountInText(((UserMessage) message).text());
            } else if (message instanceof AiMessage) {
                return tokenizer.estimateTokenCountInText(((AiMessage) message).text());
            }
            return 0;
        }
        // Mock模式降级
        String text = "";
        if (message instanceof SystemMessage) text = ((SystemMessage) message).text();
        else if (message instanceof UserMessage) text = ((UserMessage) message).text();
        else if (message instanceof AiMessage) text = ((AiMessage) message).text();
        return text.length() / 2;
    }

    /**
     * 保存对话历史
     */
    private void saveConversationHistory(QuestionRequest request, String sessionId, String response, long responseTime) {
        try {
            AIConversation conversation = new AIConversation();
            conversation.setUserId(request.getUserId());
            conversation.setSessionId(sessionId);
            conversation.setConversationType(ConversationType.QA.name());
            conversation.setUserInput(request.getQuestion());
            conversation.setAiResponse(response);
            conversation.setResponseTime(responseTime);
            conversation.setCreateTime(LocalDateTime.now());

            // 如果有房间ID，也保存
            if (request.getRoomId() != null) {
                conversation.setDocumentId(request.getRoomId()); // 复用documentId字段存储roomId
            }

            aiConversationDao.save(conversation);
            log.info("对话历史已保存，用户ID: {}, sessionId: {}", request.getUserId(), sessionId);

        } catch (Exception e) {
            log.error("保存对话历史失败", e);
            // 不抛出异常，避免影响主流程
        }
    }
}
