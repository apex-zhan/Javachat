package com.abin.mallchat.transaction.domain.entity;

import com.abin.mallchat.transaction.domain.dto.SecureInvokeDTO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Description:
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-08-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "secure_invoke_record", autoResultMap = true)
public class SecureInvokeRecord {

    public final static byte STATUS_WAIT = 1;

    public final static byte STATUS_FAIL = 2;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /**
     * 请求快照参数json
     */
    @TableField(value = "secure_invoke_json", typeHandler = JacksonTypeHandler.class)
    private SecureInvokeDTO secureInvokeDTO;
    /**
     * 状态 1待执行 2已失败
     */
    @TableField("status")
    @Builder.Default
    private byte status = SecureInvokeRecord.STATUS_WAIT;
    /**
     * 下一次重试的时间
     */
    @TableField("next_retry_time")
    @Builder.Default //如果字段为null，则使用默认值
    private Date nextRetryTime = new Date();
    /**
     * 已经重试的次数
     */
    @TableField("retry_times")
    @Builder.Default
    private Integer retryTimes = 0;
    /**
     * 最大重试次数
     */
    @TableField("max_retry_times")
    private Integer maxRetryTimes;
    /**
     * 失败原因
     */
    @TableField("fail_reason")
    private String failReason;
    /**
     * 创建时间
     */
    @TableField("create_time")
    private Date createTime;
    /**
     * 更新时间
     */
    @TableField("update_time")
    private Date updateTime;

}
