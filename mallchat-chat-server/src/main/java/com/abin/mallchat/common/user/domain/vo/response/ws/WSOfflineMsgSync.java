package com.abin.mallchat.common.user.domain.vo.response.ws;

import com.abin.mallchat.common.chat.domain.vo.response.ChatMessageResp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Description: 离线消息同步响应，用户断线重连或上线后补偿推送
 *
 * Date: 2026-05-11
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WSOfflineMsgSync {

    /**
     * 离线消息列表
     */
    private List<ChatMessageResp> msgList;

    /**
     * 当前房间最后一条消息ID，用于客户端确认同步进度
     */
    private Long lastMsgId;

    /**
     * 是否还有更多离线消息
     */
    private Boolean hasMore;
}
