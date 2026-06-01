package com.abin.mallchat.common.common.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Description: 客户端消息确认(ACK)请求DTO
 * 客户端收到消息后，携带 deliveryId 回执给服务端
 *
 * Date: 2026-05-11
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MsgAckDTO {

    /**
     * 单次投递唯一标识，服务端推送时生成
     */
    private String deliveryId;

    /**
     * 消息ID
     */
    private Long msgId;

    /**
     * 房间ID
     */
    private Long roomId;
}
