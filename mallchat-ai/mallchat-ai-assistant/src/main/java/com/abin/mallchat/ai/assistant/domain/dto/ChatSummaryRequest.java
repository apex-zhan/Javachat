package com.abin.mallchat.ai.assistant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.Date;

/**
 * 聊天内容总结请求
 *
 * @author zxw
 * @since 2025-01-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSummaryRequest {

    /**
     * 房间ID
     */
    @NotNull(message = "房间ID不能为空")
    private Long roomId;

    /**
     * 开始时间（可选，与messageCount二选一）
     */
    private Date startTime;

    /**
     * 结束时间（可选）
     */
    private Date endTime;

    /**
     * 消息数量（可选，与时间范围二选一）
     */
    @Positive(message = "消息数量必须大于0")
    private Integer messageCount;

    /**
     * 最大token数（可选，默认使用配置值）
     */
    @Positive(message = "最大token数必须大于0")
    private Integer maxTokens;
}
