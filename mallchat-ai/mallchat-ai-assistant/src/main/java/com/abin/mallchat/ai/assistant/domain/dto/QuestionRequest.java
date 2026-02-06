package com.abin.mallchat.ai.assistant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 智能问答请求
 *
 * @author zxw
 * @since 2025-01-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionRequest {

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 问题内容
     */
    @NotBlank(message = "问题内容不能为空")
    @Size(min = 1, max = 2000, message = "问题长度必须在1-2000字符之间")
    private String question;

    /**
     * 上下文信息（可选）
     * 例如：之前的对话历史、相关背景信息等
     */
    private String context;

    /**
     * 房间ID（可选，用于关联对话场景）
     */
    private Long roomId;
}
