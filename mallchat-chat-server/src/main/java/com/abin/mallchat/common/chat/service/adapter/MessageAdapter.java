package com.abin.mallchat.common.chat.service.adapter;

import cn.hutool.core.bean.BeanUtil;
import com.abin.mallchat.common.chat.domain.entity.Message;
import com.abin.mallchat.common.chat.domain.entity.MessageMark;
import com.abin.mallchat.common.chat.domain.enums.MessageMarkTypeEnum;
import com.abin.mallchat.common.chat.domain.enums.MessageStatusEnum;
import com.abin.mallchat.common.chat.domain.enums.MessageTypeEnum;
import com.abin.mallchat.common.chat.domain.vo.request.ChatMessageReq;
import com.abin.mallchat.common.chat.domain.vo.request.msg.TextMsgReq;
import com.abin.mallchat.common.chat.domain.vo.response.ChatMessageResp;
import com.abin.mallchat.common.chat.service.strategy.msg.AbstractMsgHandler;
import com.abin.mallchat.common.chat.service.strategy.msg.MsgHandlerFactory;
import com.abin.mallchat.common.common.domain.enums.YesOrNoEnum;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Description: 消息适配器
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-03-26
 */
public class MessageAdapter {

    //防止回调刷爆数据库，一次性处理过多消息
    public static final int CAN_CALLBACK_GAP_COUNT = 100;

    /**
     * 构建保存消息实体
     *
     * @param request 消息请求
     * @param uid     用户id
     * @return
     */
    public static Message buildMsgSave(ChatMessageReq request, Long uid) {

        return Message.builder()
                .fromUid(uid)
                .roomId(request.getRoomId())
                .type(request.getMsgType())
                .status(MessageStatusEnum.NORMAL.getStatus())
                .build();

    }

    /**
     * 构建消息响应VO
     *
     * @param messages
     * @param msgMark
     * @param receiveUid
     * @return
     */
    public static List<ChatMessageResp> buildMsgResp(List<Message> messages, List<MessageMark> msgMark, Long receiveUid) {
        //消息id -> [消息标记列表],
        Map<Long, List<MessageMark>> markMap = msgMark.stream().collect(Collectors.groupingBy(MessageMark::getMsgId));
        return messages.stream().map(a -> {
                    ChatMessageResp resp = new ChatMessageResp();
                    resp.setFromUser(buildFromUser(a.getFromUid()));
                    resp.setMessage(buildMessage(a, markMap.getOrDefault(a.getId(), new ArrayList<>()), receiveUid));
                    return resp;
                })
                //默认是升序，降序需要加上.reversed();
                .sorted(Comparator.comparing(a -> a.getMessage().getSendTime()))//帮前端排好序，更方便它展示
                .collect(Collectors.toList());
    }

    private static ChatMessageResp.Message buildMessage(Message message, List<MessageMark> marks, Long receiveUid) {
        ChatMessageResp.Message messageVO = new ChatMessageResp.Message();
        BeanUtil.copyProperties(message, messageVO);
        messageVO.setSendTime(message.getCreateTime());
        AbstractMsgHandler<?> msgHandler = MsgHandlerFactory.getStrategyNoNull(message.getType());
        if (Objects.nonNull(msgHandler)) {
            messageVO.setBody(msgHandler.showMsg(message));
        }
        //消息标记
        messageVO.setMessageMark(buildMsgMark(marks, receiveUid));
        return messageVO;
    }

    /**
     * 构建消息标记
     *
     * @param marks
     * @param receiveUid
     * @return
     */
    private static ChatMessageResp.MessageMark buildMsgMark(List<MessageMark> marks, Long receiveUid) {
        //对消息标记列表按消息类型进行分组，然后对每个消息类型进行分组，形成map，键为消息类型，值为消息标记列表
        Map<Integer, List<MessageMark>> typeMap = marks.stream().collect(Collectors.groupingBy(MessageMark::getType));
        //获取点赞和举报消息标记
        List<MessageMark> likeMarks = typeMap.getOrDefault(MessageMarkTypeEnum.LIKE.getType(), new ArrayList<>());
        List<MessageMark> dislikeMarks = typeMap.getOrDefault(MessageMarkTypeEnum.DISLIKE.getType(), new ArrayList<>());
        //构建消息标记
        ChatMessageResp.MessageMark mark = new ChatMessageResp.MessageMark();
        mark.setLikeCount(likeMarks.size());
        mark.setUserLike(Optional.ofNullable(receiveUid).filter(uid -> likeMarks.stream().anyMatch(a -> Objects.equals(a.getUid(), uid))).map(a -> YesOrNoEnum.YES.getStatus()).orElse(YesOrNoEnum.NO.getStatus()));
        mark.setDislikeCount(dislikeMarks.size());
        mark.setUserDislike(Optional.ofNullable(receiveUid).filter(uid -> dislikeMarks.stream().anyMatch(a -> Objects.equals(a.getUid(), uid))).map(a -> YesOrNoEnum.YES.getStatus()).orElse(YesOrNoEnum.NO.getStatus()));
        return mark;
    }

    /**
     * 构建发送者信息
     *
     * @param fromUid
     * @return
     */
    private static ChatMessageResp.UserInfo buildFromUser(Long fromUid) {
        ChatMessageResp.UserInfo userInfo = new ChatMessageResp.UserInfo();
        userInfo.setUid(fromUid);
        return userInfo;
    }

    public static ChatMessageReq buildAgreeMsg(Long roomId) {
        ChatMessageReq chatMessageReq = new ChatMessageReq();
        chatMessageReq.setRoomId(roomId);
        chatMessageReq.setMsgType(MessageTypeEnum.TEXT.getType());
        TextMsgReq textMsgReq = new TextMsgReq();
        textMsgReq.setContent("我们已经成为好友了，开始聊天吧");
        chatMessageReq.setBody(textMsgReq);
        return chatMessageReq;
    }
}
