package com.abin.mallchat.common.user.domain.enums;

import lombok.Data;

/**
 * Description: ws的基本返回信息体
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-03-19
 */
@Data
public class WSBaseResp<T> {
    /**
     * ws推送给前端的消息
     *
     * @see WSRespTypeEnum
     */
    private Integer type;
    private T data;

    /**
     * 单次投递唯一标识，用于ACK消息确认机制
     * 服务端生成，客户端收到消息后需原样回传
     */
    private String deliveryId;
}
