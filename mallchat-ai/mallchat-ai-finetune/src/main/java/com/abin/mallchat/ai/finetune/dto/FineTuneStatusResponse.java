package com.abin.mallchat.ai.finetune.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 微调任务状态响应 DTO
 *
 * @author abin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FineTuneStatusResponse {

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 任务状态
     * pending | running | completed | failed | cancelled
     */
    private String status;

    /**
     * 进度百分比 (0-100)
     */
    private Integer progress;

    /**
     * 当前步数 / 总步数
     */
    private String progressDetail;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 最新日志
     */
    private String latestLog;

    /**
     * 错误信息
     */
    private String errorMessage;
}
