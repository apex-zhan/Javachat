package com.abin.mallchat.common.chat.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.abin.mallchat.common.chat.dao.ContactDao;
import com.abin.mallchat.common.chat.dao.MessageDao;
import com.abin.mallchat.common.chat.domain.entity.Contact;
import com.abin.mallchat.common.chat.domain.entity.Message;
import com.abin.mallchat.common.chat.service.ChatService;
import com.abin.mallchat.common.common.domain.vo.request.CursorPageBaseReq;
import com.abin.mallchat.common.common.domain.vo.response.CursorPageBaseResp;
import com.abin.mallchat.common.user.domain.enums.WSBaseResp;
import com.abin.mallchat.common.user.domain.vo.response.ws.WSOfflineMsgSync;
import com.abin.mallchat.common.user.service.WebSocketService;
import com.abin.mallchat.common.user.service.adapter.WSAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Description: 消息补偿服务
 * <p>
 * 用户断线重连后，按房间查询lastMsgId之后的消息并补偿推送。
 * 与离线消息队列互补：离线队列处理"实时推送失败"，本服务处理"断线期间的消息"。
 *
 * Date: 2026-05-11
 */
@Slf4j
@Service
public class MsgCompensationService {

    /**
     * 每房间最大补偿消息数
     */
    private static final int MAX_COMPENSATION_PER_ROOM = 100;

    @Autowired
    private ContactDao contactDao;
    @Autowired
    private MessageDao messageDao;
    @Autowired
    private ChatService chatService;
    @Autowired
    @Lazy
    private WebSocketService webSocketService;

    /**
     * 用户上线时进行消息补偿
     *
     * @param uid       用户ID
     * @param lastMsgId 客户端最后一条消息ID
     */
    public void compensateOnLogin(Long uid, Long lastMsgId) {
        if (uid == null) {
            return;
        }
        try {
            // 获取用户所有会话
            List<Contact> contacts = contactDao.getByUid(uid);
            if (CollectionUtil.isEmpty(contacts)) {
                return;
            }

            for (Contact contact : contacts) {
                try {
                    compensateRoomMsgs(uid, contact.getRoomId(), lastMsgId);
                } catch (Exception e) {
                    log.error("补偿房间消息失败 uid={}, roomId={}", uid, contact.getRoomId(), e);
                }
            }
        } catch (Exception e) {
            log.error("用户{}上线消息补偿失败", uid, e);
        }
    }

    /**
     * 补偿指定房间的消息
     *
     * @param uid       用户ID
     * @param roomId    房间ID
     * @param lastMsgId 客户端最后一条消息ID
     */
    private void compensateRoomMsgs(Long uid, Long roomId, Long lastMsgId) {
        if (roomId == null) {
            return;
        }

        // 如果客户端没有lastMsgId，则不补偿（首次登录或全新设备）
        if (lastMsgId == null || lastMsgId <= 0) {
            return;
        }

        // 查询该房间lastMsgId之后的消息
        CursorPageBaseReq pageReq = new CursorPageBaseReq();
        pageReq.setPageSize(MAX_COMPENSATION_PER_ROOM);
        pageReq.setCursor(String.valueOf(lastMsgId));

        // 使用消息分页查询获取lastMsgId之后的消息
        CursorPageBaseResp<Message> msgPage = messageDao.getCursorPage(roomId, pageReq, null);
        if (msgPage == null || CollectionUtil.isEmpty(msgPage.getList())) {
            return;
        }

        List<com.abin.mallchat.common.chat.domain.vo.response.ChatMessageResp> msgRespList =
                chatService.getMsgRespBatch(msgPage.getList(), uid);

        if (CollectionUtil.isEmpty(msgRespList)) {
            return;
        }

        // 构建离线消息同步响应
        Long newLastMsgId = msgPage.getList().stream()
                .map(Message::getId)
                .filter(Objects::nonNull)
                .max(Long::compare)
                .orElse(lastMsgId);

        WSOfflineMsgSync syncData = WSOfflineMsgSync.builder()
                .msgList(msgRespList)
                .lastMsgId(newLastMsgId)
                .hasMore(!msgPage.getIsLast())
                .build();

        WSBaseResp<WSOfflineMsgSync> syncResp = WSAdapter.buildOfflineMsgSync(syncData);
        webSocketService.sendToUid(syncResp, uid);

        log.info("用户{}房间{}消息补偿完成，补偿条数={}", uid, roomId, msgRespList.size());
    }
}
