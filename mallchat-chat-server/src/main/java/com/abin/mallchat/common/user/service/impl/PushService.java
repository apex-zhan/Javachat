package com.abin.mallchat.common.user.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.abin.mallchat.common.chat.service.impl.OfflineMsgService;
import com.abin.mallchat.common.common.constant.MQConstant;
import com.abin.mallchat.common.common.domain.dto.PushMessageDTO;
import com.abin.mallchat.common.user.domain.enums.WSBaseResp;
import com.abin.mallchat.common.user.service.WebSocketService;
import com.abin.mallchat.transaction.service.MQProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Description: 推送服务
 * <p>
 * 优化点：
 * 1. 推送前检查用户是否在线，不在线则将消息保存到离线队列
 * 2. 支持批量推送时的离线消息处理
 *
 * Date: 2023-08-12
 */
@Slf4j
@Service
public class PushService {
    @Autowired(required = false)
    private MQProducer mqProducer;
    @Autowired
    private OfflineMsgService offlineMsgService;
    @Autowired
    private WebSocketService webSocketService;

    public void sendPushMsg(WSBaseResp<?> msg, List<Long> uidList) {
        if (mqProducer != null) {
            // 分离在线用户和离线用户
            List<Long> onlineUids = uidList.stream()
                    .filter(uid -> {
                        boolean online = CollectionUtil.isNotEmpty(webSocketService.getUserChannels(uid));
                        if (!online) {
                            // 用户不在线，保存到离线队列
                            offlineMsgService.saveOfflineMsg(uid, extractMsgId(msg), extractRoomId(msg), extractContent(msg));
                        }
                        return online;
                    })
                    .collect(java.util.stream.Collectors.toList());

            // 只推送在线用户
            if (CollectionUtil.isNotEmpty(onlineUids)) {
                mqProducer.sendMsg(MQConstant.PUSH_TOPIC, new PushMessageDTO(onlineUids, msg));
            }
        }
    }

    public void sendPushMsg(WSBaseResp<?> msg, Long uid) {
        if (mqProducer != null) {
            // 检查用户是否在线
            boolean online = CollectionUtil.isNotEmpty(webSocketService.getUserChannels(uid));
            if (!online) {
                // 用户不在线，保存到离线队列
                offlineMsgService.saveOfflineMsg(uid, extractMsgId(msg), extractRoomId(msg), extractContent(msg));
                return;
            }
            mqProducer.sendMsg(MQConstant.PUSH_TOPIC, new PushMessageDTO(uid, msg));
        }
    }

    public void sendPushMsg(WSBaseResp<?> msg) {
        if (mqProducer != null) {
            mqProducer.sendMsg(MQConstant.PUSH_TOPIC, new PushMessageDTO(msg));
        }
    }

    /**
     * 从消息体中提取消息ID
     * todo: 实际应根据msg的类型和data结构提取
     */
    private Long extractMsgId(WSBaseResp<?> msg) {
        if (msg.getData() instanceof com.abin.mallchat.common.chat.domain.vo.response.ChatMessageResp) {
            return ((com.abin.mallchat.common.chat.domain.vo.response.ChatMessageResp) msg.getData()).getMessage().getId();
        }
        return null;
    }

    /**
     * 从消息体中提取房间ID
     */
    private Long extractRoomId(WSBaseResp<?> msg) {
        if (msg.getData() instanceof com.abin.mallchat.common.chat.domain.vo.response.ChatMessageResp) {
            return ((com.abin.mallchat.common.chat.domain.vo.response.ChatMessageResp) msg.getData()).getMessage().getRoomId();
        }
        return null;
    }

    /**
     * 从消息体中提取内容
     */
    private String extractContent(WSBaseResp<?> msg) {
        if (msg.getData() instanceof com.abin.mallchat.common.chat.domain.vo.response.ChatMessageResp) {
            Object body = ((com.abin.mallchat.common.chat.domain.vo.response.ChatMessageResp) msg.getData()).getMessage().getBody();
            return body != null ? body.toString() : "";
        }
        return "";
    }
}
