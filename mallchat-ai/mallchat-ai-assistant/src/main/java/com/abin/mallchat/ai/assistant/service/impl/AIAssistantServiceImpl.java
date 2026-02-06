package com.abin.mallchat.ai.assistant.service.impl;

import com.abin.mallchat.ai.assistant.domain.dto.ChatSummaryRequest;
import com.abin.mallchat.ai.assistant.domain.dto.QuestionRequest;
import com.abin.mallchat.ai.assistant.service.AIAssistantService;
import com.abin.mallchat.ai.common.dao.AIConversationDao;
import com.abin.mallchat.ai.common.domain.entity.AIConversation;
import com.abin.mallchat.ai.common.domain.enums.ConversationType;
import com.abin.mallchat.ai.llm.domain.LLMOptions;
import com.abin.mallchat.ai.llm.service.LLMService;
import com.abin.mallchat.common.chat.dao.MessageDao;
import com.abin.mallchat.common.chat.domain.entity.Message;
import com.abin.mallchat.common.common.domain.vo.request.CursorPageBaseReq;
import com.abin.mallchat.common.common.domain.vo.response.CursorPageBaseResp;
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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
    private MessageDao messageDao;

    @Autowired
    private AIConversationDao aiConversationDao;

    @Autowired
    private OpenAiTokenizer tokenizer;

    @Value("${langchain4j.open-ai.chat-model.max-tokens:4096}")
    private Integer defaultMaxTokens;

    @Value("${ai.assistant.summary.system-prompt:你是一个专业的聊天内容总结助手。请根据提供的聊天记录，生成简洁、准确的总结，突出关键信息和重要讨论点。}")
    private String summarySystemPrompt;

    @Value("${ai.assistant.qa.system-prompt:你是一个智能助手，请根据用户的问题提供准确、有帮助的回答。}")
    private String qaSystemPrompt;

    @Override
    public Flux<String> summarizeChat(ChatSummaryRequest request) {
        log.info("开始总结聊天内容，房间ID: {}", request.getRoomId());

        try {
            // 1. 获取指定范围的聊天记录
            List<Message> messages = fetchChatMessages(request);

            if (messages.isEmpty()) {
                return Flux.just("没有找到聊天记录。");
            }

            // 2. 构建聊天内容文本
            String chatContent = buildChatContent(messages);

            // 3. 检查token数量，超出则截断
            int maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : defaultMaxTokens;
            String truncatedContent = truncateContentIfNeeded(chatContent, maxTokens);

            // 4. 构造总结Prompt
            String prompt = buildSummaryPrompt(truncatedContent);

            // 5. 调用LLM流式生成总结
            LLMOptions options = LLMOptions.builder()
                    .temperature(0.7)
                    .maxTokens(1000)
                    .build();

            return llmService.streamChat(prompt, options)
                    .doOnComplete(() -> log.info("聊天内容总结完成，房间ID: {}", request.getRoomId()))
                    .doOnError(e -> log.error("聊天内容总结失败，房间ID: {}", request.getRoomId(), e));

        } catch (Exception e) {
            log.error("总结聊天内容时发生错误", e);
            return Flux.just("抱歉，总结聊天内容时发生错误，请稍后再试。");
        }
    }

    @Override
    public Flux<String> answerQuestion(QuestionRequest request) {
        log.info("开始处理智能问答，用户ID: {}, 问题: {}", request.getUserId(), request.getQuestion());

        long startTime = System.currentTimeMillis();
        AtomicReference<String> fullResponse = new AtomicReference<>("");

        try {
            // 1. 验证问题内容合法性
            if (!validateQuestion(request.getQuestion())) {
                return Flux.just("抱歉，您的问题包含不合法的内容，请重新提问。");
            }

            // 2. 构造问答Prompt（可选上下文）
            String prompt = buildQuestionPrompt(request);

            // 3. 调用LLM流式生成回答
            LLMOptions options = LLMOptions.builder()
                    .temperature(0.8)
                    .maxTokens(2000)
                    .build();

            return llmService.streamChat(prompt, options)
                    .doOnNext(chunk -> {
                        // 累积完整响应
                        fullResponse.updateAndGet(current -> current + chunk);
                    })
                    .doOnComplete(() -> {
                        // 4. 保存对话历史到数据库
                        long responseTime = System.currentTimeMillis() - startTime;
                        saveConversationHistory(request, fullResponse.get(), responseTime);
                        log.info("智能问答完成，用户ID: {}, 耗时: {}ms", request.getUserId(), responseTime);
                    })
                    .doOnError(e -> {
                        log.error("智能问答失败，用户ID: {}", request.getUserId(), e);
                        // 即使失败也保存记录
                        saveConversationHistory(request, "ERROR: " + e.getMessage(), 
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
     * 构造问答Prompt
     */
    private String buildQuestionPrompt(QuestionRequest request) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(qaSystemPrompt).append("\n\n");

        // 如果有上下文信息，添加到prompt中
        if (request.getContext() != null && !request.getContext().trim().isEmpty()) {
            promptBuilder.append("上下文信息：\n");
            promptBuilder.append(request.getContext()).append("\n\n");
        }

        promptBuilder.append("用户问题：\n");
        promptBuilder.append(request.getQuestion()).append("\n\n");
        promptBuilder.append("请提供回答：");

        return promptBuilder.toString();
    }

    /**
     * 保存对话历史
     */
    private void saveConversationHistory(QuestionRequest request, String response, long responseTime) {
        try {
            AIConversation conversation = new AIConversation();
            conversation.setUserId(request.getUserId());
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
            log.info("对话历史已保存，用户ID: {}", request.getUserId());

        } catch (Exception e) {
            log.error("保存对话历史失败", e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 获取聊天记录
     */
    private List<Message> fetchChatMessages(ChatSummaryRequest request) {
        List<Message> allMessages = new ArrayList<>();

        if (request.getMessageCount() != null && request.getMessageCount() > 0) {
            // 按消息数量获取
            CursorPageBaseReq pageReq = new CursorPageBaseReq();
            pageReq.setPageSize(request.getMessageCount());
            CursorPageBaseResp<Message> page = messageDao.getCursorPage(request.getRoomId(), pageReq, null);
            allMessages.addAll(page.getList());
        } else {
            // 按时间范围获取（分页获取所有）
            CursorPageBaseReq pageReq = new CursorPageBaseReq();
            pageReq.setPageSize(100);
            String cursor = null;
            boolean hasMore = true;

            while (hasMore) {
                pageReq.setCursor(cursor);
                CursorPageBaseResp<Message> page = messageDao.getCursorPage(request.getRoomId(), pageReq, null);
                
                List<Message> pageMessages = page.getList().stream()
                        .filter(msg -> filterByTimeRange(msg, request))
                        .collect(Collectors.toList());
                
                allMessages.addAll(pageMessages);
                
                cursor = page.getCursor();
                hasMore = page.getIsLast() != null && !page.getIsLast();
                
                // 防止无限循环
                if (allMessages.size() > 10000) {
                    log.warn("聊天记录数量超过10000条，停止获取");
                    break;
                }
            }
        }

        // 按时间排序（从旧到新）
        allMessages.sort(Comparator.comparing(Message::getCreateTime));

        log.info("获取到{}条聊天记录", allMessages.size());
        return allMessages;
    }

    /**
     * 按时间范围过滤消息
     */
    private boolean filterByTimeRange(Message message, ChatSummaryRequest request) {
        if (request.getStartTime() != null && message.getCreateTime().before(request.getStartTime())) {
            return false;
        }
        if (request.getEndTime() != null && message.getCreateTime().after(request.getEndTime())) {
            return false;
        }
        return true;
    }

    /**
     * 构建聊天内容文本
     */
    private String buildChatContent(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message message : messages) {
            sb.append(String.format("[%s] 用户%d: %s\n",
                    message.getCreateTime(),
                    message.getFromUid(),
                    message.getContent()));
        }
        return sb.toString();
    }

    /**
     * 检查token数量并截断内容
     */
    private String truncateContentIfNeeded(String content, int maxTokens) {
        // 为系统提示和总结输出预留token
        int reservedTokens = 500;
        int availableTokens = maxTokens - reservedTokens;

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(summarySystemPrompt));
        messages.add(new UserMessage(content));

        int tokenCount = tokenizer.estimateTokenCountInMessages(messages);

        if (tokenCount <= maxTokens) {
            log.info("内容token数量: {}, 在限制范围内", tokenCount);
            return content;
        }

        log.warn("内容token数量: {}, 超出限制: {}, 需要截断", tokenCount, maxTokens);

        // 简单截断策略：按字符比例截断
        double ratio = (double) availableTokens / tokenCount;
        int targetLength = (int) (content.length() * ratio * 0.9); // 0.9是安全系数

        String truncated = content.substring(0, Math.min(targetLength, content.length()));
        truncated += "\n\n[注：由于内容过长，部分聊天记录已被截断]";

        log.info("截断后内容长度: {}", truncated.length());
        return truncated;
    }

    /**
     * 构造总结Prompt
     */
    private String buildSummaryPrompt(String chatContent) {
        return String.format("%s\n\n以下是需要总结的聊天记录：\n\n%s\n\n请生成总结：",
                summarySystemPrompt,
                chatContent);
    }
}
