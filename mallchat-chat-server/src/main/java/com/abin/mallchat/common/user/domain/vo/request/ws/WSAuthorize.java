package com.abin.mallchat.common.user.domain.vo.request.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Description:
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-03-19
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WSAuthorize {
    private String token;

    /**
     * 设备类型：1-PC, 2-APP, 3-WEB
     */
    private Integer deviceType;

    /**
     * 客户端最后一条消息ID，用于断线重连补偿
     */
    private Long lastMsgId;
}
